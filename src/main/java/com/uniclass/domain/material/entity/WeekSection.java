package com.uniclass.domain.material.entity;

import com.uniclass.domain.classroom.entity.ClassRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "week_sections", uniqueConstraints = {
        @UniqueConstraint(name = "UK_classroom_week_number", columnNames = {"classroom_id", "week_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeekSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassRoom classroom;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @OneToMany(mappedBy = "weekSection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Material> materials = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public WeekSection(ClassRoom classroom, int weekNumber, String title, String description) {
        this.classroom = classroom;
        this.weekNumber = weekNumber;
        this.title = title != null ? title : (weekNumber + "주차");
        this.description = description;
    }

    public void updateTitleAndDescription(String title, String description) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.description = description;
    }
}
