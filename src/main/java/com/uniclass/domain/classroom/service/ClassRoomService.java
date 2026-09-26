package com.uniclass.domain.classroom.service;

import com.uniclass.domain.classroom.dto.ClassRoomCardDto;
import com.uniclass.domain.classroom.dto.CreateClassRoomDto;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ClassRoomService {

    private static final String CODE_CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // 헷갈리기 쉬운 0, 1, I, O 제외
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    public ClassRoomService(ClassRoomRepository classRoomRepository,
                            EnrollmentRepository enrollmentRepository,
                            UserRepository userRepository) {
        this.classRoomRepository = classRoomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Long createClassRoom(Long instructorId, CreateClassRoomDto dto) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("교수 정보를 찾을 수 없습니다."));

        if (instructor.getRole() != Role.ROLE_INSTRUCTOR) {
            throw new IllegalStateException("수업 개설은 교수 권한(ROLE_INSTRUCTOR)만 가능합니다.");
        }

        String inviteCode = generateUniqueInviteCode();

        ClassRoom classRoom = ClassRoom.builder()
                .name(dto.getName().trim())
                .courseCode(dto.getCourseCode().trim())
                .semester(dto.getSemester().trim())
                .division(dto.getDivision().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : "")
                .inviteCode(inviteCode)
                .instructor(instructor)
                .build();

        return classRoomRepository.save(classRoom).getId();
    }

    @Transactional
    public Long joinClassRoom(Long studentId, String inviteCode) {
        String normalizedCode = inviteCode.trim().toUpperCase();

        ClassRoom classRoom = classRoomRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("해당 초대 코드와 일치하는 수업을 찾을 수 없습니다."));

        if (classRoom.isArchived()) {
            throw new IllegalArgumentException("종료되어 보관(아카이빙)된 수업에는 참가할 수 없습니다.");
        }

        if (classRoom.getInstructor().getId().equals(studentId)) {
            throw new IllegalArgumentException("담당 교수는 자신의 수업에 수강생으로 참가할 수 없습니다.");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다."));

        Optional<Enrollment> existingOpt = enrollmentRepository.findByStudentIdAndClassroomId(studentId, classRoom.getId());
        if (existingOpt.isPresent()) {
            Enrollment existing = existingOpt.get();
            if (existing.getStatus() == EnrollmentStatus.ENROLLED) {
                throw new IllegalArgumentException("이미 수강 신청이 완료된 수업입니다.");
            } else {
                existing.reEnroll();
                return classRoom.getId();
            }
        }

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .classroom(classRoom)
                .status(EnrollmentStatus.ENROLLED)
                .build();

        enrollmentRepository.save(enrollment);
        return classRoom.getId();
    }

    public List<ClassRoomCardDto> getInstructorClassrooms(Long instructorId) {
        List<ClassRoom> classes = classRoomRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        return classes.stream().map(c -> {
            long studentCount = enrollmentRepository.countByClassroomIdAndStatus(c.getId(), EnrollmentStatus.ENROLLED);
            return ClassRoomCardDto.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .courseCode(c.getCourseCode())
                    .semester(c.getSemester())
                    .division(c.getDivision())
                    .inviteCode(c.getInviteCode())
                    .instructorName(c.getInstructor().getName())
                    .studentCount(studentCount)
                    .isArchived(c.isArchived())
                    .build();
        }).collect(Collectors.toList());
    }

    public List<ClassRoomCardDto> getStudentClassrooms(Long studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentIdAndStatusWithClassroom(studentId, EnrollmentStatus.ENROLLED);
        return enrollments.stream().map(e -> {
            ClassRoom c = e.getClassroom();
            long studentCount = enrollmentRepository.countByClassroomIdAndStatus(c.getId(), EnrollmentStatus.ENROLLED);
            return ClassRoomCardDto.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .courseCode(c.getCourseCode())
                    .semester(c.getSemester())
                    .division(c.getDivision())
                    .inviteCode(c.getInviteCode())
                    .instructorName(c.getInstructor().getName())
                    .studentCount(studentCount)
                    .isArchived(c.isArchived())
                    .build();
        }).collect(Collectors.toList());
    }

    public ClassRoom getClassRoomDetail(Long classroomId) {
        return classRoomRepository.findByIdWithInstructor(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다."));
    }

    public boolean isUserEnrolledOrInstructor(Long classroomId, Long userId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다."));
        if (classRoom.getInstructor().getId().equals(userId)) {
            return true;
        }
        return enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(userId, classroomId, EnrollmentStatus.ENROLLED);
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
            }
            String code = sb.toString();
            if (!classRoomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성에 실패했습니다. 다시 시도해 주세요.");
    }
}
