package com.uniclass.global.init;

import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.entity.Submission;
import com.uniclass.domain.assignment.entity.SubmissionStatus;
import com.uniclass.domain.assignment.repository.AssignmentRepository;
import com.uniclass.domain.assignment.repository.SubmissionRepository;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.material.service.MaterialService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(name = "uniclass.init-test-data", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class TestDataInit implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MaterialService materialService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        log.info("==== [UniClass] 테스트용 초기 시드 데이터 생성을 시작합니다 ====");

        // 1. 교수 계정 생성
        User prof = userRepository.save(User.builder()
                .email("prof@uniclass.ac.kr")
                .password(passwordEncoder.encode("password123!"))
                .name("김교수")
                .studentNo("PROF001")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        // 2. 학생 계정 1, 2 생성
        User student1 = userRepository.save(User.builder()
                .email("student@uniclass.ac.kr")
                .password(passwordEncoder.encode("password123!"))
                .name("이학생")
                .studentNo("20261111")
                .role(Role.ROLE_STUDENT)
                .build());

        User student2 = userRepository.save(User.builder()
                .email("student2@uniclass.ac.kr")
                .password(passwordEncoder.encode("password123!"))
                .name("박컴공")
                .studentNo("20262222")
                .role(Role.ROLE_STUDENT)
                .build());

        // 3. 샘플 강의실 개설
        ClassRoom classRoom = classRoomRepository.save(ClassRoom.builder()
                .name("자료구조 및 실습")
                .courseCode("CS101")
                .semester("2026-1학기")
                .division("01분반")
                .description("컴퓨터공학과 전공 필수 자료구조 과목입니다. 기본 선형/비선형 자료구조와 알고리즘을 학습합니다.")
                .inviteCode("CS101A")
                .instructor(prof)
                .build());

        // 1~16주차 자동 생성
        materialService.initializeDefaultWeeks(classRoom);

        // 4. 학생 수강 신청 등록 (2명 모두 ENROLLED)
        enrollmentRepository.save(Enrollment.builder()
                .classroom(classRoom)
                .student(student1)
                .status(EnrollmentStatus.ENROLLED)
                .build());

        enrollmentRepository.save(Enrollment.builder()
                .classroom(classRoom)
                .student(student2)
                .status(EnrollmentStatus.ENROLLED)
                .build());

        // 5. 샘플 과제 생성
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .classroom(classRoom)
                .title("[과제 1] 연결 리스트(LinkedList) 직접 구현하기")
                .description("C언어 또는 Java를 사용하여 단일 연결 리스트(Singly LinkedList)의 삽입, 삭제, 탐색 연산을 직접 구현하고 보고서를 제출하세요.")
                .dueDate(LocalDateTime.now().plusDays(7))
                .maxScore(100)
                .allowLate(true)
                .build());

        // 6. 학생 1(이학생) 샘플 과제 제출 등록
        try {
            java.nio.file.Path dummyDir = java.nio.file.Paths.get("./uploads/submissions");
            java.nio.file.Files.createDirectories(dummyDir);
            java.nio.file.Path dummyFile = dummyDir.resolve("sample_dummy.pdf");
            if (!java.nio.file.Files.exists(dummyFile)) {
                java.nio.file.Files.writeString(dummyFile, "%PDF-1.4 UniClass Sample Assignment Report Content");
            }
        } catch (Exception e) {
            log.warn("샘플 과제 더미 파일 생성 실패: {}", e.getMessage());
        }

        submissionRepository.save(Submission.builder()
                .assignment(assignment)
                .student(student1)
                .originalFilename("LinkedList_20261111_이학생.pdf")
                .filePath("submissions/sample_dummy.pdf")
                .fileSize(1024L)
                .status(SubmissionStatus.SUBMITTED)
                .build());

        log.info("==== [UniClass] 테스트용 초기 시드 데이터 생성이 완료되었습니다 ====");
    }
}
