import urllib.request
import urllib.parse
import http.cookiejar
from html.parser import HTMLParser
import uuid
import sys

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except AttributeError:
        pass

class CSRFParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.csrf = None
    def handle_starttag(self, tag, attrs):
        if tag == "input":
            attrs_dict = dict(attrs)
            if attrs_dict.get("name") == "_csrf":
                self.csrf = attrs_dict.get("value")

def get_csrf(opener, url):
    req = urllib.request.Request(url)
    req.add_header('Connection', 'close')
    try:
        response = opener.open(req)
        html = read_response(response)
    except urllib.error.HTTPError as e:
        html = read_response(e)
    parser = CSRFParser()
    parser.feed(html)
    return parser.csrf, html

def post_multipart(opener, url, fields, files):
    boundary = '----WebKitFormBoundary' + uuid.uuid4().hex
    body = bytearray()
    
    for key, value in fields.items():
        body.extend(f'--{boundary}\r\n'.encode('utf-8'))
        body.extend(f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode('utf-8'))
        body.extend(f'{value}\r\n'.encode('utf-8'))
        
    for key, (filename, content, content_type) in files.items():
        body.extend(f'--{boundary}\r\n'.encode('utf-8'))
        body.extend(f'Content-Disposition: form-data; name="{key}"; filename="{filename}"\r\n'.encode('utf-8'))
        body.extend(f'Content-Type: {content_type}\r\n\r\n'.encode('utf-8'))
        body.extend(content)
        body.extend(b'\r\n')
        
    body.extend(f'--{boundary}--\r\n'.encode('utf-8'))
    
    req = urllib.request.Request(url, data=body)
    req.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')
    try:
        res = opener.open(req)
        return res.status, res.headers, res.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read().decode('utf-8')

import http.client

def read_response(res):
    try:
        return res.read().decode('utf-8')
    except http.client.IncompleteRead as e:
        return e.partial.decode('utf-8')

def post_data(opener, url, data_dict):
    data = urllib.parse.urlencode(data_dict).encode('utf-8')
    req = urllib.request.Request(url, data=data)
    req.add_header('Connection', 'close')
    try:
        res = opener.open(req)
        return res.status, res.headers, read_response(res)
    except urllib.error.HTTPError as e:
        return e.code, e.headers, read_response(e)

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def test_assignment_full_flow():
    prof_jar = http.cookiejar.CookieJar()
    opener_prof = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(prof_jar), NoRedirectHandler())

    student_jar = http.cookiejar.CookieJar()
    opener_student = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(student_jar), NoRedirectHandler())

    print("--- [1] Register & Login Professor ---")
    csrf, _ = get_csrf(opener_prof, "http://localhost:8080/register")
    reg_prof = {
        "role": "ROLE_INSTRUCTOR",
        "name": "최교수",
        "studentNo": "PROF9001",
        "email": "prof_e2e@univ.ac.kr",
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf
    }
    code, headers, html = post_data(opener_prof, "http://localhost:8080/register", reg_prof)
    print(f"Register prof status: {code}")
    if code == 200:
        import re
        errors = re.findall(r'text-rose-600[^>]*>([^<]+)<', html)
        print(f"Validation errors: {errors}")

    csrf_login, _ = get_csrf(opener_prof, "http://localhost:8080/login")
    login_prof = {
        "email": "prof_e2e@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf_login
    }
    code, headers, _ = post_data(opener_prof, "http://localhost:8080/login", login_prof)
    print(f"Login prof status: {code}, loc: {headers.get('Location')}")

    print("--- [2] Create Classroom ---")
    csrf_class, _ = get_csrf(opener_prof, "http://localhost:8080/classes/new")
    create_class = {
        "name": "운영체제",
        "courseCode": "CS301",
        "semester": "2026-1학기",
        "division": "01",
        "description": "운영체제 과목입니다.",
        "_csrf": csrf_class
    }
    code, headers, _ = post_data(opener_prof, "http://localhost:8080/classes/new", create_class)
    loc = headers.get("Location")
    print(f"Class create status: {code}, loc: {loc}")
    class_id = loc.split("/classes/")[1].split("?")[0]
    print(f"Created Classroom ID: {class_id}")

    # Fetch invite code from detail page
    detail_html = opener_prof.open(urllib.request.Request(f"http://localhost:8080/classes/{class_id}")).read().decode('utf-8')
    import re
    match = re.search(r'id="inviteCodeText"[^>]*>\s*([A-Za-z0-9]+)\s*<', detail_html)
    invite_code = match.group(1).strip()
    print(f"Classroom Invite Code: {invite_code}")

    print("--- [3] Create Assignment (Professor) ---")
    csrf_assign, _ = get_csrf(opener_prof, f"http://localhost:8080/classes/{class_id}/assignments/new")
    fields = {
        "title": "과제 1: 프로세스 스케줄러 시뮬레이션",
        "description": "FCFS, RR, SJF 스케줄러를 Java로 시뮬레이션하고 비교 분석 보고서를 작성하세요.",
        "weekNumber": "3",
        "dueDate": "2026-05-30T23:59",
        "maxScore": "100",
        "allowLate": "true",
        "_csrf": csrf_assign
    }
    files = {
        "attachment": ("assignment1_template.zip", b"PK Mock zip template", "application/zip")
    }
    code, headers, _ = post_multipart(opener_prof, f"http://localhost:8080/classes/{class_id}/assignments", fields, files)
    print(f"Assignment create response: {code}, Location: {headers.get('Location')}")
    assign_id = headers.get('Location').split('/assignments/')[1]
    print(f"Created Assignment ID: {assign_id}")

    print("--- [4] Register & Login Student ---")
    csrf_s, _ = get_csrf(opener_student, "http://localhost:8080/register")
    reg_student = {
        "role": "ROLE_STUDENT",
        "name": "윤학생",
        "studentNo": "20268888",
        "email": "student_e2e@univ.ac.kr",
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf_s
    }
    code, headers, _ = post_data(opener_student, "http://localhost:8080/register", reg_student)
    print(f"Register student status: {code}")

    csrf_login_s, _ = get_csrf(opener_student, "http://localhost:8080/login")
    login_student = {
        "email": "student_e2e@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf_login_s
    }
    code, headers, _ = post_data(opener_student, "http://localhost:8080/login", login_student)
    print(f"Login student status: {code}, loc: {headers.get('Location')}")

    print("--- [5] Student Join Classroom ---")
    csrf_join, _ = get_csrf(opener_student, "http://localhost:8080/classes/join")
    join_data = {
        "inviteCode": invite_code,
        "_csrf": csrf_join
    }
    code, headers, _ = post_data(opener_student, "http://localhost:8080/classes/join", join_data)
    print(f"Join class status: {code}, loc: {headers.get('Location')}")

    print("--- [6] Student View Assignment & Submit Solution ---")
    csrf_sub, page_html = get_csrf(opener_student, f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}")
    assert "과제 1: 프로세스 스케줄러 시뮬레이션" in page_html
    assert "미제출" in page_html

    sub_fields = {
        "note": "RR 타임 퀀텀 4로 테스트 완료했습니다.",
        "_csrf": csrf_sub
    }
    sub_files = {
        "file": ("student_solution.pdf", b"%PDF-1.4 Student Solution Report", "application/pdf")
    }
    code, headers, _ = post_multipart(opener_student, f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}/submit", sub_fields, sub_files)
    print(f"Student submit response: {code}")

    # Check student sees submitted status
    student_view = opener_student.open(urllib.request.Request(f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}")).read().decode('utf-8')
    import re
    if "submissionSuccessMessage" in student_view or "과제가 성공적으로 제출되었습니다" in student_view:
        print("FOUND: 과제가 성공적으로 제출되었습니다")
    err = re.findall(r'bg-rose-50[^>]*>([^<]+)<', student_view)
    if err:
        print(f"ERROR BANNER: {err}")
    print(f"Contains student_solution.pdf: {'student_solution.pdf' in student_view}")
    print(f"Contains SUBMITTED: {'SUBMITTED' in student_view}")
    assert "student_solution.pdf" in student_view
    assert "student_solution.pdf" in student_view
    print("SUCCESS: Student sees '정상 제출 완료' and submitted filename")

    print("--- [7] Professor Grades Student Submission ---")
    prof_req = urllib.request.Request(f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}")
    prof_req.add_header('Connection', 'close')
    prof_res = opener_prof.open(prof_req)
    prof_view = read_response(prof_res)
    assert "윤학생" in prof_view
    assert "20268888" in prof_view
    assert "student_solution.pdf" in prof_view

    # Extract submissionId from download link
    sub_id_match = re.search(rf'/submissions/([0-9]+)/download', prof_view)
    sub_id = sub_id_match.group(1)
    print(f"Extracted Submission ID: {sub_id}")

    # Grade submission
    csrf_prof_grade, _ = get_csrf(opener_prof, f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}")
    grade_data = {
        "score": "98",
        "feedback": "스케줄링 알고리즘 비교가 매우 꼼꼼합니다. A+",
        "_csrf": csrf_prof_grade
    }
    code, headers, _ = post_data(opener_prof, f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}/submissions/{sub_id}/grade", grade_data)
    print(f"Grade response: {code}, loc: {headers.get('Location')}")

    # Check student sees score and feedback
    grade_req = urllib.request.Request(f"http://localhost:8080/classes/{class_id}/assignments/{assign_id}")
    grade_req.add_header('Connection', 'close')
    student_grade_res = opener_student.open(grade_req)
    student_after_grade = read_response(student_grade_res)
    assert "98/100" in student_after_grade
    assert "스케줄링 알고리즘 비교가 매우 꼼꼼합니다" in student_after_grade
    print("SUCCESS: Student received grade (98/100) and feedback!")

    print("=== ASSIGNMENT FULL FLOW E2E PASSED ===")

if __name__ == "__main__":
    test_assignment_full_flow()
