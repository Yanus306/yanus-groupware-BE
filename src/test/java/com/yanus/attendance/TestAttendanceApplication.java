package com.yanus.attendance;

import org.springframework.boot.SpringApplication;

public class TestAttendanceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AttendanceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
