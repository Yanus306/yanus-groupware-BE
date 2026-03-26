# GitHub Actions CI/CD 트러블슈팅

---

## CI

### 1. CI 워크플로우가 실행되지 않음

**원인**
워크플로우 파일 위치가 잘못됨.
```
잘못된 위치: .github/ci.yml
올바른 위치: .github/workflows/ci.yml
```

---

### 2. CI에서 WeakKeyException으로 테스트 실패

**증상**
```
Caused by: io.jsonwebtoken.security.WeakKeyException
```

**원인**
GitHub Secrets에 32자 미만의 JWT 시크릿 키가 설정되어 있었음.

**해결**
`src/test/resources/application.properties`에 테스트 전용 키 지정.
```properties
jwt.secret=test-secret-key-for-ci-must-be-at-least-32-characters-long
```

---

## CD

### 1. SERVER_HOST에 도메인을 넣어 SSH 접속 실패

**증상**
```
dial tcp <CLOUDFLARE-IP>:22: i/o timeout
```

**원인**
도메인이 Cloudflare 프록시를 거치고 있어 SSH 대상으로 적절하지 않음.

**해결**
`SERVER_HOST` 값을 도메인이 아닌 공인 IP로 변경.

---

### 2. GitHub Actions에서 SSH 개인키를 인식하지 못한 문제

**증상**
```
ssh.ParsePrivateKey: ssh: no key found
```

**원인**
공개키(`.pub`)를 넣었거나 줄바꿈/형식이 깨진 상태.

**해결**
배포 전용 SSH 키 생성 후 개인키 전체를 `SERVER_SSH_KEY` Secret에 등록.
```
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

---

### 3. 서버에 공개키가 등록되지 않아 SSH 인증 실패

**해결**
```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
cat github_actions_deploy.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

---

### 4. GitHub Actions가 서버의 22번 포트에 접속하지 못한 문제

**증상**
```
dial tcp <SERVER-IP>:22: i/o timeout
```

**원인**
공유기/망 환경상 외부 22 포트가 막혀 있었음.

**해결**
공유기에서 외부 `2222` 포트를 내부 `22` 포트로 포트포워딩.
```
SERVER_PORT=2222
```

---

### 5. scp-action으로 업로드한 JAR 경로가 예상과 달랐던 문제

**증상**
```
cp: cannot stat '/home/yanus/app/attendance-0.0.1-SNAPSHOT.jar': No such file or directory
```

**원인**
`source: "build/libs/..."` 로 업로드 시 디렉터리 구조가 그대로 포함되어 업로드됨.

**실제 업로드 경로**
```
/home/yanus/app/build/libs/attendance-0.0.1-SNAPSHOT.jar
```

**해결**
```bash
cp /home/yanus/app/build/libs/attendance-0.0.1-SNAPSHOT.jar /home/yanus/app/app.jar
```

---

### 6. sudo systemctl restart 실행 시 비밀번호 요구

**증상**
```
sudo: a terminal is required to read the password
```

**원인**
GitHub Actions는 비대화형 환경이라 sudo 비밀번호 입력이 불가능.

**해결**
```bash
sudo visudo -f /etc/sudoers.d/yanus
```
```
yanus ALL=(ALL) NOPASSWD: /usr/bin/systemctl
```

---

### 7. /etc/sudoers 본문에 직접 넣은 규칙이 중복

**해결**
사용자별 커스텀 sudo 규칙은 `/etc/sudoers.d/<username>` 파일로 분리.

---

### 8. PR 이벤트와 배포 트리거 혼동

**해결**
배포 워크플로는 `main` 브랜치 `push` 이벤트만 사용.
```yaml
on:
  push:
    branches: [ main ]
```

---

### 9. Required status checks가 바로 보이지 않던 문제

**원인**
PR에서 CI가 한 번 돌았더라도 Ruleset 화면에서 바로 selectable하게 뜨지 않는 경우가 있음.

**해결**
CI 워크플로를 실행시켜 최근 성공 이력을 만든 뒤 다시 확인.
