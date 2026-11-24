import assert from 'node:assert/strict'
import { execFileSync, spawn } from 'node:child_process'
import { closeSync, openSync } from 'node:fs'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import net from 'node:net'
import { resolve } from 'node:path'
import readline from 'node:readline'
import { setTimeout as delay } from 'node:timers/promises'

const [jar, mode, output, priorJar] = process.argv.slice(2)
assert(jar && ['baseline', 'candidate', 'load', 'upgrade', 'delivery-failure'].includes(mode) && output,
  'Usage: node lab.mjs APP.jar baseline|candidate|load|upgrade|delivery-failure OUTPUT_DIR [PRIOR_JAR]')
const directory = resolve(output)
await mkdir(directory, { recursive: true })

const postgres = 'yanus232-' + process.pid
const applications = new Set()
const monitorContainers = []
const smtpConnections = new Set()
const messageAttempts = []
let smtpDelay = 0
let smtpReply = '250 2.0.0 queued'
let appPort
let managementPort
let dbPort
let teamId

function docker(...args) {
  return execFileSync('docker', args, { encoding: 'utf8' }).trim()
}

function sql(statement) {
  return docker('exec', postgres, 'psql', '-XAt', '-v', 'ON_ERROR_STOP=1', '-U', 'lab', '-d', 'lab', '-c', statement)
}

async function until(check, description, waitMs = 30000) {
  const deadline = Date.now() + waitMs
  do {
    const result = await check()
    if (result) return result
    await delay(50)
  } while (Date.now() < deadline)
  throw new Error('Timed out: ' + description)
}

function listen(server) {
  return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port)))
}

async function port() {
  const server = net.createServer()
  const value = await listen(server)
  await new Promise(resolve => server.close(resolve))
  return value
}

const smtp = net.createServer(socket => {
  smtpConnections.add(socket)
  socket.once('close', () => smtpConnections.delete(socket))
  socket.on('error', () => {})
  let inData = false
  let recipient = ''
  const lines = readline.createInterface({ input: socket })
  socket.write('220 local.test ESMTP\r\n')
  lines.on('line', async line => {
    if (inData) {
      if (line !== '.') return
      inData = false
      const attempt = { recipient, started: Date.now(), accepted: false }
      messageAttempts.push(attempt)
      const reply = smtpReply
      await delay(smtpDelay)
      if (!socket.destroyed && socket.writable) {
        attempt.accepted = reply.startsWith('250')
        attempt.finished = Date.now()
        socket.write(reply + '\r\n')
      }
      return
    }
    const verb = line.split(' ')[0].toUpperCase()
    if (verb === 'EHLO' || verb === 'HELO') {
      socket.write('250 local.test\r\n')
    } else if (verb === 'RCPT') {
      recipient = line.match(/<([^>]+)>/)?.[1] ?? ''
      socket.write('250 2.1.5 ok\r\n')
    } else if (verb === 'DATA') {
      inData = true
      socket.write('354 end with dot\r\n')
    } else if (verb === 'QUIT') {
      socket.end('221 bye\r\n')
    } else {
      socket.write('250 ok\r\n')
    }
  })
})
const smtpPort = await listen(smtp)

async function stop(child, signal = 'SIGTERM') {
  if (child.exitCode !== null || child.signalCode !== null) return
  await new Promise(resolve => {
    child.once('exit', resolve)
    child.kill(signal)
  })
}

async function start(label, workerEnabled = true, appJar = jar) {
  const fd = openSync(directory + '/' + label + '.log', 'w')
  const child = spawn('java', ['-XX:ActiveProcessorCount=4', '-Xmx768m', '-jar', appJar,
    '--server.address=127.0.0.1', '--server.port=' + appPort,
    '--management.server.port=' + managementPort,
    '--management.endpoints.web.exposure.include=health,info,prometheus',
    '--spring.datasource.hikari.maximum-pool-size=4',
    '--spring.datasource.hikari.minimum-idle=1',
    '--spring.mail.host=127.0.0.1', '--spring.mail.port=' + smtpPort,
    '--spring.mail.properties.mail.smtp.auth=false',
    '--spring.mail.properties.mail.smtp.from=lab@local.test',
    '--spring.mail.properties.mail.smtp.starttls.enable=false',
    '--spring.mail.properties.mail.smtp.connectiontimeout=500',
    '--spring.mail.properties.mail.smtp.timeout=3000',
    '--spring.mail.properties.mail.smtp.writetimeout=3000',
    '--yanus.email-verification.worker-enabled=' + workerEnabled,
    '--yanus.email-verification.poll-delay=100',
    '--logging.level.com.yanus.attendance=INFO',
    '--management.metrics.distribution.percentiles-histogram.http.server.requests=true'
  ], {
    env: { ...process.env,
      DB_URL: 'jdbc:postgresql://127.0.0.1:' + dbPort + '/lab', DB_USERNAME: 'lab', DB_PASSWORD: 'lab',
      JWT_SECRET: 'local-lab-test-key-32-bytes-or-longer-1234',
      MINIO_ENDPOINT: 'http://127.0.0.1:9', MINIO_ACCESS_KEY: 'test', MINIO_SECRET_KEY: 'test-test',
      MAIL_USERNAME: 'lab@local.test', MAIL_PASSWORD: 'lab'
    },
    stdio: ['ignore', fd, fd]
  })
  closeSync(fd)
  applications.add(child)
  await until(async () => {
    assert(child.exitCode === null, 'Application exited; inspect ' + label + '.log')
    try {
      return (await fetch('http://127.0.0.1:' + managementPort + '/actuator/health/readiness')).ok
    } catch {
      return false
    }
  }, 'application readiness')
  return child
}

async function request(route, payload, token) {
  const started = performance.now()
  const result = await fetch('http://127.0.0.1:' + appPort + '/api/v1/auth/' + route, {
    method: payload ? 'POST' : 'GET',
    headers: { ...(payload && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: 'Bearer ' + token }) },
    body: payload && JSON.stringify(payload),
    signal: AbortSignal.timeout(12000)
  })
  const body = await result.json()
  return { status: result.status, ms: Math.round((performance.now() - started) * 100) / 100, body }
}

function register(email) {
  return request('register', { name: 'Lab User', email, password: 'lab-pass-123', teamId })
}

async function hikari() {
  const result = await fetch('http://127.0.0.1:' + managementPort + '/actuator/prometheus')
  assert.equal(result.status, 200)
  return Object.fromEntries([...((await result.text()).matchAll(/^(hikaricp_connections_(?:active|pending|acquire_seconds_count|acquire_seconds_sum))\{[^}]+\}\s+(\S+)$/gm))]
    .map(([, name, value]) => [name, Number(value)]))
}

function memberState(email) {
  assert(/^[a-z0-9.-]+@local\.test$/.test(email))
  return sql("SELECT status FROM member WHERE email='" + email + "'")
}

function tokenFor(email) {
  assert(/^[a-z0-9.-]+@local\.test$/.test(email))
  return sql("SELECT t.token FROM email_verification_token t JOIN member m ON m.member_id=t.member_id WHERE m.email='" + email + "'")
}

function hasJobs() {
  return sql("SELECT to_regclass('public.email_verification_job')") === 'email_verification_job'
}

function jobForRecipient(email) {
  assert(/^[a-z0-9.-]+@local\.test$/.test(email))
  return sql(`SELECT j.id || '|' || j.status || '|' || j.attempt_count || '|' || coalesce(j.last_error_code, '')
     FROM email_verification_job j JOIN email_verification_token t ON t.id=j.token_id
     JOIN member m ON m.member_id=t.member_id WHERE m.email='${email}'`)
}

async function exerciseDeliveryFailures(app) {
  smtpReply = '451 4.3.0 simulated temporary error'
  assert.equal((await register('retry@local.test')).status, 200)
  const retry = await until(() => {
    const state = jobForRecipient('retry@local.test')
    return state.endsWith('|PENDING|1|SMTP_451') && state
  }, 'temporary SMTP failure is recorded as retry')
  smtpReply = '250 2.0.0 queued'
  const recovered = await until(() => {
    const state = jobForRecipient('retry@local.test')
    return state.endsWith('|SENT|2|') && state
  }, 'real retry after SMTP recovery')
  smtpReply = '550 5.1.1 simulated permanent error'
  assert.equal((await register('permanent@local.test')).status, 200)
  const permanent = await until(() => {
    const state = jobForRecipient('permanent@local.test')
    return state.endsWith('|FAILED|1|SMTP_550') && state
  }, 'permanent SMTP failure')

  smtpReply = '250 2.0.0 queued'
  smtpDelay = 1900
  assert.equal((await register('crash@local.test')).status, 200)
  const crashAttempt = await until(() => messageAttempts.find(message => message.recipient === 'crash@local.test'),
    'worker reaches SMTP')
  const jobId = Number(jobForRecipient('crash@local.test').split('|')[0])
  assert(jobId > 0)
  const locker = spawn('docker', ['exec', '-e', 'PGAPPNAME=yanus232-fixture-lock', postgres,
    'psql', '-XAt', '-U', 'lab', '-d', 'lab',
    '-c', `BEGIN; SELECT id FROM email_verification_job WHERE id=${jobId} FOR UPDATE; SELECT pg_sleep(20); COMMIT;`],
  { stdio: 'ignore' })
  try {
    await until(() => sql(`SELECT 1 FROM pg_stat_activity
      WHERE application_name='yanus232-fixture-lock' AND wait_event='PgSleep'`), 'fixture blocks outcome row')
    assert.equal(crashAttempt.accepted, false)
    await until(() => crashAttempt.accepted && sql(`SELECT 1 FROM pg_stat_activity
      WHERE wait_event_type='Lock' AND query LIKE 'UPDATE email_verification_job%'`), 'SMTP accepted and result write blocked')
    await stop(app, 'SIGKILL')
  } finally {
    sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name='yanus232-fixture-lock'")
    await stop(locker)
  }
  const crashed = jobForRecipient('crash@local.test')
  assert(crashed.endsWith('|PROCESSING|1|'))
  sql(`UPDATE email_verification_job SET lease_until=now()-interval '1 second' WHERE id=${jobId}`)
  smtpDelay = 0
  await start('restarted-after-acceptance')
  const crashRecovered = await until(() => {
    const state = jobForRecipient('crash@local.test')
    return state.endsWith('|SENT|2|') && state
  }, 'result write recovered after lease expiration')
  const acceptedAfterCrash = messageAttempts.filter(message => message.recipient === 'crash@local.test' && message.accepted).length
  assert.equal(acceptedAfterCrash, 2)
  const token = tokenFor('crash@local.test')
  assert.equal((await request('verify-email', { token })).status, 200)
  assert.equal((await request('verify-email', { token })).status, 400)
  return { retry, recovered, permanent, crashed, crashRecovered, acceptedAfterCrash,
    leaseExpiredByFixture: true, member: memberState('crash@local.test') }
}

async function startMonitoring() {
  const prometheus = postgres + '-prom'
  const grafana = postgres + '-grafana'
  const config = directory + '/prometheus.yml'
  await writeFile(config, `global:
  scrape_interval: 500ms
scrape_configs:
  - job_name: yanus232
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.docker.internal:${managementPort}']
`)
  docker('run', '--rm', '-d', '--name', prometheus, '-p', '127.0.0.1::9090',
    '-v', config + ':/etc/prometheus/prometheus.yml:ro', 'prom/prometheus:v2.45.3')
  monitorContainers.push(prometheus)
  const promPort = docker('port', prometheus, '9090/tcp').split(':').at(-1)
  const provision = directory + '/datasource.yml'
  await writeFile(provision, `apiVersion: 1
datasources:
  - name: Yanus232
    uid: yanus232
    type: prometheus
    access: proxy
    url: http://host.docker.internal:${promPort}
`)
  docker('run', '--rm', '-d', '--name', grafana, '-p', '127.0.0.1::3000',
    '-e', 'GF_SECURITY_ADMIN_PASSWORD=lab',
    '-v', provision + ':/etc/grafana/provisioning/datasources/yanus232.yaml:ro', 'grafana/grafana:13.2.3')
  monitorContainers.push(grafana)
  const grafanaPort = docker('port', grafana, '3000/tcp').split(':').at(-1)
  const proxy = 'http://127.0.0.1:' + grafanaPort + '/api/datasources/proxy/uid/yanus232/api/v1/'
  const headers = { Authorization: 'Basic ' + Buffer.from('admin:lab').toString('base64') }
  await until(async () => {
    try {
      const response = await fetch(proxy + 'query?query=up%7Bjob%3D%22yanus232%22%7D', { headers })
      return response.ok && (await response.json()).data.result.some(item => item.value[1] === '1')
    } catch {
      return false
    }
  }, 'Grafana data source reaches the application')
  return async (query, start, end) => {
    const params = new URLSearchParams({ query, start: String(start / 1000), end: String(end / 1000), step: '0.5' })
    const response = await fetch(proxy + 'query_range?' + params, { headers })
    assert.equal(response.status, 200)
    return (await response.json()).data.result
  }
}

function percentile(numbers, fraction) {
  const ordered = numbers.toSorted((a, b) => a - b)
  return ordered[Math.ceil(ordered.length * fraction) - 1] ?? null
}

async function runLoad() {
  const queued = hasJobs()
  const registered = await register('reader@local.test')
  assert.equal(registered.status, 200)
  if (queued) await until(() => sql("SELECT 1 FROM email_verification_job WHERE status='SENT'"), 'seed mail')
  const verified = await request('verify-email', { token: tokenFor('reader@local.test') })
  assert.equal(verified.status, 200)
  const login = await request('login', { email: 'reader@local.test', password: 'lab-pass-123' })
  assert.equal(login.status, 200)
  const accessToken = login.body.data.accessToken
  assert.equal((await request('me', null, accessToken)).status, 200)
  sql(`INSERT INTO member(name, email, password, role, team_id, created_at, status)
       SELECT 'Resend Fixture', 'pending-' || s || '@local.test', m.password, 'MEMBER', m.team_id, now(), 'PENDING'
       FROM member m CROSS JOIN generate_series(1,100) s WHERE m.email='reader@local.test'`)
  const seconds = Number(process.env.YANUS_LAB_SECONDS ?? '6')
  assert(Number.isInteger(seconds) && seconds >= 2 && seconds <= 40)
  smtpDelay = Number(process.env.YANUS_LAB_SMTP_DELAY_MS ?? '1200')
  assert(Number.isInteger(smtpDelay) && smtpDelay >= 0 && smtpDelay <= 2500)
  const queryGrafana = await startMonitoring()
  const sampleStart = Date.now()
  const previousAttempts = messageAttempts.length
  const initial = await hikari()
  const reportRoot = directory + '/gatling'
  const log = openSync(directory + '/gatling.log', 'w')
  const simulation = spawn('mvn', ['-o', '-B', 'gatling:test',
    '-f', resolve(import.meta.dirname, 'gatling/pom.xml'),
    '-Dgatling.resultsFolder=' + reportRoot, '-DbaseUrl=http://127.0.0.1:' + appPort,
    '-DteamId=' + teamId, '-Dseconds=' + seconds, '-DrunId=load'], {
    env: { ...process.env, YANUS_LAB_ACCESS_TOKEN: accessToken },
    stdio: ['ignore', log, log]
  })
  closeSync(log)
  const exit = new Promise((accept, reject) => {
    simulation.once('error', reject)
    simulation.once('exit', accept)
  })
  const samples = []
  while (simulation.exitCode === null && simulation.signalCode === null) {
    samples.push({ at: Date.now(), ...await hikari() })
    await delay(125)
  }
  assert.equal(await exit, 0, 'Gatling failed; read gatling.log')
  const sampledUntil = Date.now()
  const endOfHttp = await hikari()
  if (queued) {
    await until(() => sql("SELECT count(*) FROM email_verification_job WHERE status IN ('PENDING','PROCESSING')") === '0',
      'drain delivery queue', 150000)
  }
  const report = reportRoot + '/' + (await readdir(reportRoot)).find(name => name.startsWith('emailverificationsimulation-'))
  const endpoints = {}
  for (const file of (await readdir(report)).filter(name => /^req_(register|resend|me)-/.test(name))) {
    const html = await readFile(report + '/' + file, 'utf8')
    const metric = label => {
      const row = html.match(new RegExp('<td class="title">' + label + '</td>\\s*<td class="total">([^<]+)</td>\\s*<td class="ok">([^<]+)</td>\\s*<td class="ko">([^<]+)</td>'))
      assert(row, 'Metric not found: ' + label)
      return row.slice(1).map(value => value.trim() === '-' ? null : Number(value.replaceAll(',', '').trim()))
    }
    const [total, success, failure] = metric('Total count')
    const name = file.match(/^req_([a-z]+)-/)[1]
    endpoints[name] = { total, success, failure, p50: metric('50th percentile')[0], p95: metric('95th percentile')[0] }
  }
  assert.equal(Object.keys(endpoints).length, 3)
  for (const endpoint of Object.values(endpoints)) assert.equal(endpoint.success, endpoint.total)
  const readings = Object.fromEntries(await Promise.all([
    'hikaricp_connections_active', 'hikaricp_connections_pending', 'hikaricp_connections_acquire_seconds_sum',
    'http_server_requests_seconds_count', 'yanus_email_verification_delivery_seconds_count'
  ].map(async name => [name, await queryGrafana(name, sampleStart, Date.now())])))
  assert(readings.hikaricp_connections_pending.length > 0)
  await writeFile(directory + '/grafana-prometheus.json', JSON.stringify(readings, null, 2) + '\n')
  await writeFile(directory + '/hikari-samples.json', JSON.stringify(samples, null, 2) + '\n')
  const actualMessages = messageAttempts.slice(previousAttempts).filter(message => message.accepted)
  const lastAcceptance = Math.max(...actualMessages.map(message => message.finished))
  const summary = {
    queued, smtpDelayMs: smtpDelay, injectionSeconds: seconds, endpoints,
    hikari: {
      maxActive: Math.max(...samples.map(sample => sample.hikaricp_connections_active)),
      maxPending: Math.max(...samples.map(sample => sample.hikaricp_connections_pending)),
      acquireWaitTotalMs: Math.round((endOfHttp.hikaricp_connections_acquire_seconds_sum -
        initial.hikaricp_connections_acquire_seconds_sum) * 100000) / 100,
      grafanaPeakPending: Math.max(...readings.hikaricp_connections_pending.flatMap(series => series.values.map(([, n]) => Number(n))))
    },
    mail: {
      accepted: actualMessages.length,
      smtpDataToAcceptanceP95Ms: percentile(actualMessages.map(message => message.finished - message.started), 0.95),
      afterHttpReportFinishedMs: Math.max(0, lastAcceptance - sampledUntil),
      jobToSentP95Ms: queued ? Number(sql(`SELECT round(percentile_disc(0.95) WITHIN GROUP
        (ORDER BY extract(epoch FROM updated_at - created_at) * 1000)) FROM email_verification_job
         WHERE status='SENT' AND id > 1`)) : null,
      queueOutcomes: queued ? sql("SELECT status || '|' || count(*) FROM email_verification_job GROUP BY status") : null
    }
  }
  return summary
}

try {
  const address = docker('run', '--rm', '--detach', '--name', postgres, '-e', 'POSTGRES_USER=lab',
    '-e', 'POSTGRES_PASSWORD=lab', '-e', 'POSTGRES_DB=lab', '-p', '127.0.0.1::5432', 'postgres:16.15')
  assert(address)
  dbPort = docker('port', postgres, '5432/tcp').split(':').at(-1)
  appPort = await port()
  managementPort = await port()

  let evidence = {}
  if (mode === 'upgrade') assert(priorJar)
  let app = await start('first', mode !== 'candidate', mode === 'upgrade' ? priorJar : jar)
  teamId = Number(sql("INSERT INTO team (name, created_at) VALUES ('lab team', now()) RETURNING team_id").split('\n')[0])
  assert(teamId > 0)
  if (mode === 'baseline') {
    smtpDelay = 1200
    const delayed = register('delayed@local.test').then(result => {
      assert.equal(result.status, 200, JSON.stringify(result.body))
      return result
    })
    await until(() => messageAttempts.length === 1, 'SMTP reaches DATA')
    evidence.duringSmtp = { hikari: await hikari(), committedStatus: memberState('delayed@local.test') }
    const accepted = await delayed
    evidence.delayed = { status: accepted.status, ms: accepted.ms, member: memberState('delayed@local.test') }
    assert.equal(accepted.status, 200)
    assert.equal(evidence.duringSmtp.committedStatus, '')
    smtpDelay = 0
    smtpReply = '451 4.3.0 simulated temporary error'
    const failed = await register('failure@local.test')
    evidence.failure = { status: failed.status, member: memberState('failure@local.test') }
    assert.equal(failed.status, 500)
    assert.equal(evidence.failure.member, '')
    smtpReply = '250 2.0.0 queued'
    sql(`CREATE FUNCTION fixture_reject_commit() RETURNS trigger LANGUAGE plpgsql AS $$
         BEGIN RAISE EXCEPTION 'Fixture commit failure'; END; $$;
         CREATE CONSTRAINT TRIGGER fixture_fail_commit AFTER INSERT ON member
         DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
         WHEN (NEW.email = 'commit-fail@local.test') EXECUTE FUNCTION fixture_reject_commit()`)
    const commitFailed = await register('commit-fail@local.test')
    evidence.commitFailure = { status: commitFailed.status, member: memberState('commit-fail@local.test'),
      previouslyAccepted: messageAttempts.some(message => message.recipient === 'commit-fail@local.test' && message.accepted) }
    assert.deepEqual(evidence.commitFailure, { status: 500, member: '', previouslyAccepted: true })
  } else if (mode === 'candidate') {
    const received = await register('restart@local.test')
    assert.equal(received.status, 200)
    assert.equal(messageAttempts.length, 0)
    evidence.beforeRestart = { apiMs: received.ms, member: memberState('restart@local.test'),
      job: sql('SELECT status FROM email_verification_job') }
    assert.equal(evidence.beforeRestart.job, 'PENDING')
    await stop(app, 'SIGKILL')
    smtpDelay = 1200
    app = await start('restarted')
    await until(() => messageAttempts.length === 1, 'SMTP after restart')
    evidence.duringSmtp = { hikari: await hikari(), committedStatus: memberState('restart@local.test') }
    await until(() => sql("SELECT 1 FROM email_verification_job WHERE status='SENT'"), 'sent after restart')
    const expectedToken = tokenFor('restart@local.test')
    const verified = await request('verify-email', { token: expectedToken })
    evidence.afterRestart = { delivered: messageAttempts.filter(x => x.accepted).length,
      verifyStatus: verified.status, member: memberState('restart@local.test') }
    assert.equal(verified.status, 200)
    assert.equal(evidence.afterRestart.member, 'ACTIVE')
  } else if (mode === 'upgrade') {
    assert.equal((await register('upgrade@local.test')).status, 200)
    assert.equal(hasJobs(), false)
    await stop(app)
    app = await start('migrated', false)
    assert.equal(hasJobs(), true)
    const legacyToken = tokenFor('upgrade@local.test')
    assert.equal((await request('verify-email', { token: legacyToken })).status, 200)
    assert.equal((await register('rollback@local.test')).status, 200)
    assert.equal(sql('SELECT status FROM email_verification_job'), 'PENDING')
    await stop(app)
    app = await start('rolled-back-binary', true, priorJar)
    assert.equal(memberState('upgrade@local.test'), 'ACTIVE')
    assert.equal(memberState('rollback@local.test'), 'PENDING')
    await stop(app)
    app = await start('resumed')
    await until(() => sql("SELECT 1 FROM email_verification_job WHERE status='SENT'"), 'recover after roll-forward')
    evidence = { oldMember: memberState('upgrade@local.test'), candidateMember: memberState('rollback@local.test'),
      jobAfterRollForward: sql('SELECT status FROM email_verification_job'), accepted: messageAttempts.filter(x => x.accepted).length }
  } else if (mode === 'delivery-failure') {
    evidence = await exerciseDeliveryFailures(app)
  } else {
    evidence = await runLoad()
  }
  await writeFile(directory + '/result.json', JSON.stringify(evidence, null, 2) + '\n')
  console.log(JSON.stringify(evidence, null, 2))
} finally {
  for (const child of applications) await stop(child)
  for (const socket of smtpConnections) socket.destroy()
  smtp.close()
  for (const container of monitorContainers.reverse()) docker('rm', '-f', container)
  try {
    docker('rm', '-f', postgres)
  } catch {}
}
