# -*- coding: utf-8 -*-
import urllib.request
import urllib.parse
import http.cookiejar
import re

def get_html(email, password, url):
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    res = opener.open('http://localhost:8080/login')
    login_page = res.read().decode('utf-8')
    csrf_token = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page).group(1)
    login_data = urllib.parse.urlencode({'email': email, 'password': password, '_csrf': csrf_token}).encode('utf-8')
    opener.open(urllib.request.Request('http://localhost:8080/login', data=login_data))
    return opener.open(url).read().decode('utf-8')

# Test 1: Professor
prof_overview = get_html('prof@uniclass.ac.kr', 'password123!', 'http://localhost:8080/classes/1')
prof_grades = get_html('prof@uniclass.ac.kr', 'password123!', 'http://localhost:8080/classes/1?tab=grades')

# Test 2: Student
stud_overview = get_html('student@uniclass.ac.kr', 'password123!', 'http://localhost:8080/classes/1')
stud_grades = get_html('student@uniclass.ac.kr', 'password123!', 'http://localhost:8080/classes/1?tab=grades')

results = []

# Prof checks
results.append(("Prof Overview - Contains '학급 평균 성적'", '학급 평균 성적' in prof_overview, True))
results.append(("Prof Grades - Contains '학급 평균 성적'", '학급 평균 성적' in prof_grades, True))
results.append(("Prof Grades - Contains '수강생 수'", '수강생 수' in prof_grades, True))
results.append(("Prof Grades - Contains student '이학생'", '이학생' in prof_grades, True))
results.append(("Prof Grades - Contains other student '박컴공'", '박컴공' in prof_grades, True))

# Student checks
# Clean Thymeleaf stripped comments just in case
stud_overview_clean = re.sub(r'<!--.*?-->', '', stud_overview, flags=re.DOTALL)
stud_grades_clean = re.sub(r'<!--.*?-->', '', stud_grades, flags=re.DOTALL)

results.append(("Student Overview - Contains '내 종합 성적'", '내 종합 성적' in stud_overview_clean, True))
results.append(("Student Overview - Does NOT contain '학급 평균 성적'", '학급 평균 성적' not in stud_overview_clean, True))

results.append(("Student Grades - Does NOT contain '학급 평균 성적'", '학급 평균 성적' not in stud_grades_clean, True))
results.append(("Student Grades - Contains personal '내 종합 점수'", '내 종합 점수' in stud_grades_clean, True))
results.append(("Student Grades - Contains self '이학생'", '이학생' in stud_grades_clean, True))
results.append(("Student Grades - Does NOT contain other student '박컴공'", '박컴공' not in stud_grades_clean, True))
results.append(("Student Grades - Contains privacy protection banner", '본인 성적만 안전하게 열람' in stud_grades_clean, True))

with open('final_privacy_report.txt', 'w', encoding='utf-8') as f:
    f.write("=== FINAL PRIVACY & GRADE VISIBILITY VERIFICATION REPORT ===\n\n")
    all_passed = True
    for desc, actual, expected in results:
        status = "PASS" if actual == expected else "FAIL"
        if status == "FAIL":
            all_passed = False
        f.write(f"[{status}] {desc} (Actual: {actual}, Expected: {expected})\n")
    f.write(f"\nOVERALL RESULT: {'ALL PASS' if all_passed else 'SOME FAILED'}\n")

print(f"Report written. All passed: {all_passed}")
assert all_passed, "Some privacy checks failed!"
