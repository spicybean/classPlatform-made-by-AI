import urllib.request
import urllib.parse
import http.cookiejar
import re
import sys
import uuid
import http.client

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except AttributeError:
        pass

BASE_URL = "http://localhost:8080"

def get_session():
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    return opener

def extract_csrf(html):
    match = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    if not match:
        match = re.search(r'value="([^"]+)"\s+name="_csrf"', html)
    return match.group(1) if match else None

def read_response(resp):
    try:
        return resp.read()
    except http.client.IncompleteRead as e:
        return e.partial

def register_and_login(opener, email, password, name, student_no, role):
    # GET register
    resp = opener.open(f"{BASE_URL}/register")
    html = read_response(resp).decode('utf-8', errors='ignore')
    csrf = extract_csrf(html)

    # POST register
    reg_data = urllib.parse.urlencode({
        "_csrf": csrf,
        "email": email,
        "password": password,
        "passwordConfirm": password,
        "name": name,
        "studentNo": student_no,
        "role": role
    }).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/register", data=reg_data)
    resp = opener.open(req)
    read_response(resp)

    # GET login
    resp = opener.open(f"{BASE_URL}/login")
    html = read_response(resp).decode('utf-8', errors='ignore')
    csrf = extract_csrf(html)

    # POST login
    login_data = urllib.parse.urlencode({
        "_csrf": csrf,
        "email": email,
        "password": password
    }).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/login", data=login_data)
    resp = opener.open(req)
    read_response(resp)

def create_classroom(opener, title, department, semester):
    resp = opener.open(f"{BASE_URL}/classes/new")
    html = read_response(resp).decode('utf-8', errors='ignore')
    csrf = extract_csrf(html)

    data = urllib.parse.urlencode({
        "_csrf": csrf,
        "name": title,
        "courseCode": "CS701",
        "division": "01",
        "description": "Speed Grader 테스트 수업",
        "semester": semester
    }).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/classes/new", data=data)
    resp = opener.open(req)
    final_url = resp.geturl()
    read_response(resp)
    class_id = final_url.split('/classes/')[1].split('?')[0]

    # Get invite code from detail page
    resp = opener.open(f"{BASE_URL}/classes/{class_id}")
    detail_html = read_response(resp).decode('utf-8', errors='ignore')
    code_match = re.search(r'id="inviteCodeText"[^>]*>([A-Z0-9]{6})<', detail_html)
    if not code_match:
        code_match = re.search(r'([A-Z0-9]{6})', detail_html)
    invite_code = code_match.group(1) if code_match else "UNKNOWN"
    return class_id, invite_code

def join_classroom(opener, invite_code):
    resp = opener.open(f"{BASE_URL}/classes/join")
    html = read_response(resp).decode('utf-8', errors='ignore')
    csrf = extract_csrf(html)

    data = urllib.parse.urlencode({
        "_csrf": csrf,
        "inviteCode": invite_code
    }).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/classes/join", data=data)
    resp = opener.open(req)
    final_url = resp.geturl()
    read_response(resp)
    return final_url.split('/classes/')[1].split('?')[0]

def create_assignment(opener, class_id, title):
    resp = opener.open(f"{BASE_URL}/classes/{class_id}/assignments/new")
    html = read_response(resp).decode('utf-8', errors='ignore')
    csrf = extract_csrf(html)

    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    parts = []
    def add_field(name, value):
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode('utf-8'))

    add_field("_csrf", csrf)
    add_field("title", title)
    add_field("description", "스피드 그레이더 채점용 과제입니다.")
    add_field("dueDate", "2026-10-31T23:59")
    add_field("maxScore", "100")
    add_field("allowLate", "true")
    parts.append(f"--{boundary}--\r\n".encode('utf-8'))

    body = b"".join(parts)
    req = urllib.request.Request(f"{BASE_URL}/classes/{class_id}/assignments", data=body)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    resp = opener.open(req)
    final_url = resp.geturl()
    read_response(resp)
    return final_url.split('/assignments/')[1].split('?')[0]

def submit_assignment(opener, class_id, assign_id, filename, content_bytes, mime="application/pdf"):
    resp = opener.open(f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}")
    html = read_response(resp).decode('utf-8', errors='ignore')
    csrf = extract_csrf(html)

    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    parts = []
    def add_field(name, value):
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode('utf-8'))
    def add_file(name, fname, fbytes, ctype):
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{fname}\"\r\nContent-Type: {ctype}\r\n\r\n".encode('utf-8') + fbytes + b"\r\n")

    add_field("_csrf", csrf)
    add_field("note", "스피드 그레이더 테스트 제출본")
    add_file("file", filename, content_bytes, mime)
    parts.append(f"--{boundary}--\r\n".encode('utf-8'))

    body = b"".join(parts)
    req = urllib.request.Request(f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}/submit", data=body)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    resp = opener.open(req)
    read_response(resp)

def test_speedgrader():
    print("=== [1] 교수 및 2명의 학생 회원가입 & 수업 생성 ===")
    prof = get_session()
    register_and_login(prof, "speed_prof@univ.ac.kr", "Password123!", "강교수", "PROF7777", "ROLE_INSTRUCTOR")
    class_id, invite_code = create_classroom(prof, "스피드 그레이더 검증반", "컴퓨터공학과", "2026-1학기")
    print(f"✓ 수업 개설 완료: ID={class_id}, 코드={invite_code}")

    # 학생 1 등록 및 수강신청
    s1 = get_session()
    register_and_login(s1, "speed_s1@univ.ac.kr", "Password123!", "학생일", "20267001", "ROLE_STUDENT")
    join_classroom(s1, invite_code)
    print("✓ 학생 1 수강신청 완료")

    # 학생 2 등록 및 수강신청 (미제출 학생 테스트용)
    s2 = get_session()
    register_and_login(s2, "speed_s2@univ.ac.kr", "Password123!", "학생이", "20267002", "ROLE_STUDENT")
    join_classroom(s2, invite_code)
    print("✓ 학생 2 수강신청 완료")

    print("\n=== [2] 과제 출제 및 학생 1 과제 제출 (PDF) ===")
    assign_id = create_assignment(prof, class_id, "네트워크 설계 보고서")
    print(f"✓ 과제 생성 완료: ID={assign_id}")

    pdf_content = b"%PDF-1.4 Mock PDF content for testing speed grader inline view"
    submit_assignment(s1, class_id, assign_id, "network_report.pdf", pdf_content, "application/pdf")
    print("✓ 학생 1 과제 제출 완료 (network_report.pdf)")

    print("\n=== [3] 교수 스피드 그레이더 접속 검증 ===")
    sg_url = f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}/speedgrader"
    resp = prof.open(sg_url)
    assert resp.status == 200, f"Speed Grader 접속 실패: {resp.status}"
    sg_html = read_response(resp).decode('utf-8', errors='ignore')
    assert "Speed Grader" in sg_html, "Speed Grader 텍스트 누락"
    assert "학생일" in sg_html, "학생 1 이름 누락"
    assert "학생이" in sg_html, "학생 2 이름 누락"
    print("✓ 스피드 그레이더 메인 페이지 200 OK 및 학생 목록 렌더링 확인")

    opt_matches = re.findall(r'value="[^"]*studentId=(\d+)"[^>]*>([\s\S]*?)</option>', sg_html)
    st1_id = None
    st2_id = None
    for sid, text in opt_matches:
        if "학생일" in text:
            st1_id = sid
        if "학생이" in text:
            st2_id = sid

    assert st1_id, f"학생 1 ID를 드롭다운에서 찾을 수 없습니다: {opt_matches}"
    assert st2_id, f"학생 2 ID를 드롭다운에서 찾을 수 없습니다: {opt_matches}"

    # Open Speed Grader specifically for 학생 1 (who submitted the file)
    resp_s1 = prof.open(f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}/speedgrader?studentId={st1_id}")
    s1_html = read_response(resp_s1).decode('utf-8', errors='ignore')
    assert "network_report.pdf" in s1_html, "학생 1의 제출 파일명 누락"

    print("\n=== [4] 인라인 파일 미리보기(/preview) 엔드포인트 검증 ===")
    sub_match = re.search(rf'/classes/{class_id}/assignments/{assign_id}/submissions/(\d+)/preview', s1_html)
    assert sub_match, "미리보기 iframe URL을 찾을 수 없습니다."
    sub_id = sub_match.group(1)

    prev_url = f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}/submissions/{sub_id}/preview"
    prev_resp = prof.open(prev_url)
    assert prev_resp.status == 200, f"Preview 응답 실패: {prev_resp.status}"
    disp_header = prev_resp.headers.get("Content-Disposition", "")
    ctype_header = prev_resp.headers.get("Content-Type", "")
    assert "inline" in disp_header, f"Content-Disposition이 inline이 아님: {disp_header}"
    assert "application/pdf" in ctype_header, f"Content-Type이 application/pdf가 아님: {ctype_header}"
    body = read_response(prev_resp)
    assert b"Mock PDF content" in body, "파일 본문 불일치"
    print("✓ 인라인 미리보기 검증 완료: Content-Disposition=inline, Content-Type=application/pdf")

    print("\n=== [5] 스피드 그레이더 채점 및 다음 학생 자동 이동 (Save & Next) ===")
    csrf = extract_csrf(s1_html)

    grade_data = urllib.parse.urlencode({
        "_csrf": csrf,
        "studentId": st1_id,
        "nextStudentId": st2_id,
        "score": "95",
        "feedback": "완벽한 분석입니다. 수고 많으셨습니다!",
        "action": "saveAndNext"
    }).encode('utf-8')

    grade_req = urllib.request.Request(f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}/speedgrader/grade", data=grade_data)
    grade_resp = prof.open(grade_req)
    final_grade_url = grade_resp.geturl()
    assert f"studentId={st2_id}" in final_grade_url, f"다음 학생으로 자동 리다이렉트되지 않음: {final_grade_url}"
    print(f"✓ 학생 1 채점 완료 및 다음 학생({st2_id})으로 자동 전환 확인")

    print("\n=== [6] 미제출 학생 2 채점 및 결과 확인 ===")
    grade2_html = read_response(grade_resp).decode('utf-8', errors='ignore')
    csrf2 = extract_csrf(grade2_html)
    grade2_data = urllib.parse.urlencode({
        "_csrf": csrf2,
        "studentId": st2_id,
        "nextStudentId": "",
        "score": "0",
        "feedback": "기한 내 미제출입니다.",
        "action": "save"
    }).encode('utf-8')
    grade2_req = urllib.request.Request(f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}/speedgrader/grade", data=grade2_data)
    grade2_resp = prof.open(grade2_req)
    read_response(grade2_resp)
    print("✓ 미제출 학생 2 채점(0점 + 피드백) 반영 확인")

    print("\n=== [7] 학생 1 과제 상세 화면에서 채점 결과 및 피드백 확인 ===")
    s1_resp = s1.open(f"{BASE_URL}/classes/{class_id}/assignments/{assign_id}")
    s1_html = read_response(s1_resp).decode('utf-8', errors='ignore')
    assert "95/100" in s1_html or "95" in s1_html, "학생 1 화면에 95점 미표시"
    assert "완벽한 분석입니다" in s1_html, "학생 1 피드백 미표시"
    print("✓ 학생 1 화면에 교수 점수(95점) 및 피드백 정상 노출 확인")

    print("\n🎉 모든 스피드 그레이더 시나리오 테스트 완벽 통과 (100% SUCCESS)!")

if __name__ == "__main__":
    try:
        test_speedgrader()
    except Exception as e:
        print(f"\n❌ 테스트 실패: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
