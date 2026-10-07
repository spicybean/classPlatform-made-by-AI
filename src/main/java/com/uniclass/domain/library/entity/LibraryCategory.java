package com.uniclass.domain.library.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LibraryCategory {
    LECTURE_NOTE("강의교안", "fa-book-open", "text-blue-600 bg-blue-50 border-blue-200"),
    ASSIGNMENT_TEMPLATE("과제양식", "fa-file-lines", "text-emerald-600 bg-emerald-50 border-emerald-200"),
    EXAM_PAPER("시험자료", "fa-pen-to-square", "text-purple-600 bg-purple-50 border-purple-200"),
    REFERENCE("참고자료", "fa-bookmark", "text-amber-600 bg-amber-50 border-amber-200"),
    ETC("기타자료", "fa-folder", "text-slate-600 bg-slate-50 border-slate-200");

    private final String displayName;
    private final String iconClass;
    private final String badgeClass;
}
