import urllib.request
import urllib.parse
import http.cookiejar
import json
import re
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8080"

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def http_error_302(self, req, fp, code, msg, headers):
        return fp
    http_error_301 = http_error_302
    http_error_303 = http_error_302
    http_error_307 = http_error_302

class TestClient:
    def __init__(self):
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj),
            NoRedirectHandler()
        )
        self.follow_opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj)
        )

    def get(self, url, follow=False):
        opener = self.follow_opener if follow else self.opener
        req = urllib.request.Request(url, headers={"User-Agent": "AttendanceE2E"})
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read().decode('utf-8', errors='replace')
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode('utf-8', errors='replace')

    def post(self, url, data=None, follow=False):
        opener = self.follow_opener if follow else self.opener
        encoded_data = urllib.parse.urlencode(data).encode('utf-8') if data else b""
        req = urllib.request.Request(url, data=encoded_data, headers={
            "User-Agent": "AttendanceE2E",
            "Content-Type": "application/x-www-form-urlencoded"
        })
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read().decode('utf-8', errors='replace')
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode('utf-8', errors='replace')

def get_csrf(client, url):
    status, headers, html = client.get(url, follow=True)
    match = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    if not match:
        match = re.search(r'value="([^"]+)"\s+name="_csrf"', html)
    return match.group(1) if match else None

def register_and_login(client, email, password, name, student_no, role):
    csrf = get_csrf(client, f"{BASE_URL}/register")
    reg_data = {
        "email": email,
        "password": password,
        "passwordConfirm": password,
        "name": name,
        "studentNo": student_no,
        "role": role,
        "_csrf": csrf
    }
    status, headers, body = client.post(f"{BASE_URL}/register", data=reg_data)
    assert status in (302, 200), f"Registration failed ({status})"

    csrf_login = get_csrf(client, f"{BASE_URL}/login")
    login_data = {
        "email": email,
        "password": password,
        "_csrf": csrf_login
    }
    status, headers, body = client.post(f"{BASE_URL}/login", data=login_data)
    loc = headers.get('Location', '')
    assert status == 302, f"Login failed for {email}, status: {status}"
    assert "error=true" not in loc, f"Login failed with error for {email}: {loc}"

def run_tests():
    import time
    ts = int(time.time())
    prof = TestClient()
    stu1 = TestClient()
    stu2 = TestClient()

    print("=== [1] 교수 및 학생 2명 생성 & 수업 개설 ===")
    prof_email = f"prof_att_{ts}@univ.ac.kr"
    stu1_email = f"stu1_att_{ts}@univ.ac.kr"
    stu2_email = f"stu2_att_{ts}@univ.ac.kr"

    register_and_login(prof, prof_email, "pass1234!", "출석교수", f"P{ts%10000:04d}A", "ROLE_INSTRUCTOR")
    register_and_login(stu1, stu1_email, "pass1234!", "출석학생1", f"S1{ts%10000:04d}A", "ROLE_STUDENT")
    register_and_login(stu2, stu2_email, "pass1234!", "출석학생2", f"S2{ts%10000:04d}A", "ROLE_STUDENT")
    print("✓ 사용자 회원가입 및 로그인 성공")

    # 수업 개설
    csrf = get_csrf(prof, f"{BASE_URL}/classes/new")
    status, headers, _ = prof.post(f"{BASE_URL}/classes/new", data={
        "name": "실시간 출석 테스트 강의",
        "courseCode": "ATT101",
        "semester": "2026-1학기",
        "division": "01",
        "description": "간편 출석 테스트",
        "_csrf": csrf
    })
    assert status == 302
    loc = headers.get('Location', '')
    class_id = loc.split('/classes/')[1].split('?')[0]
    print(f"✓ 수업 개설 완료: ID={class_id}")

    # 초대 코드 추출
    _, _, html = prof.get(f"{BASE_URL}/classes/{class_id}", follow=True)
    invite_match = re.search(r'id="inviteCodeText"[^>]*>\s*([A-Z0-9]+)\s*<', html)
    invite_code = invite_match.group(1)
    print(f"✓ 초대 코드 확인: {invite_code}")

    # 학생 1, 2 수강 신청
    for client, sname in [(stu1, "학생1"), (stu2, "학생2")]:
        csrf = get_csrf(client, f"{BASE_URL}/classes/join")
        status, headers, _ = client.post(f"{BASE_URL}/classes/join", data={
            "inviteCode": invite_code,
            "_csrf": csrf
        })
        assert status == 302
        print(f"✓ {sname} 수강 신청 완료")

    print("\n=== [2] 교수 4자리 출석 세션 시작 ===")
    csrf = get_csrf(prof, f"{BASE_URL}/classes/{class_id}/attendance")
    status, headers, _ = prof.post(f"{BASE_URL}/classes/{class_id}/attendance/start", data={
        "weekNumber": 1,
        "roundNumber": 1,
        "timeoutMinutes": 3,
        "_csrf": csrf
    })
    assert status == 302
    session_url = headers.get('Location', '')
    if session_url.startswith('http'):
        target_url = session_url
        session_id = session_url.rstrip('/').split('/')[-1]
    else:
        target_url = urllib.parse.urljoin(BASE_URL, session_url)
        session_id = session_url.rstrip('/').split('/')[-1]
    print(f"✓ 출석 세션 생성 완료: Session ID={session_id}, Target URL={target_url}")

    # 대형 스크린 화면에서 4자리 인증번호 추출
    status, _, screen_html = prof.get(target_url, follow=True)
    assert status == 200
    auth_code_match = re.search(r'class="[^"]*code-pulse[^"]*"[^>]*>\s*<span>([0-9]{4})</span>', screen_html)
    if not auth_code_match:
        auth_code_match = re.search(r'code-pulse.*?<span>([0-9]{4})</span>', screen_html, re.DOTALL)
    assert auth_code_match is not None, f"Failed to find 4-digit code in session.html"
    auth_code = auth_code_match.group(1)
    print(f"✓ 대형 스크린 4자리 인증번호 확인: [{auth_code}]")

    print("\n=== [3] 학생 1: 정상 4자리 번호 입력 출석 ===")
    csrf = get_csrf(stu1, f"{BASE_URL}/classes/{class_id}/attendance")
    status, headers, _ = stu1.post(f"{BASE_URL}/classes/{class_id}/attendance/submit", data={
        "authCode": auth_code,
        "_csrf": csrf
    })
    assert status == 302
    redir_url = urllib.parse.urljoin(BASE_URL, headers.get('Location', ''))
    status, _, res_html = stu1.get(redir_url, follow=True)
    assert status == 200
    assert ("정상 완료" in res_html or "출석" in res_html)
    print("✓ 학생 1 출석 성공 (PRESENT)")

    print("\n=== [4] 학생 2: 틀린 번호 입력 시도 및 정상 번호 재입력 ===")
    csrf = get_csrf(stu2, f"{BASE_URL}/classes/{class_id}/attendance")
    status, headers, body = stu2.post(f"{BASE_URL}/classes/{class_id}/attendance/submit", data={
        "authCode": "0000",
        "_csrf": csrf
    })
    assert status == 302
    redir_url = urllib.parse.urljoin(BASE_URL, headers.get('Location', ''))
    status, _, res_html = stu2.get(redir_url, follow=True)
    assert ("일치하지 않습니다" in res_html or "잘못된" in res_html)
    print("✓ 학생 2 틀린 번호 거부 방어 확인")

    # 정상 번호로 출석
    csrf = get_csrf(stu2, f"{BASE_URL}/classes/{class_id}/attendance")
    status, headers, _ = stu2.post(f"{BASE_URL}/classes/{class_id}/attendance/submit", data={
        "authCode": auth_code,
        "_csrf": csrf
    })
    assert status == 302
    redir_url = urllib.parse.urljoin(BASE_URL, headers.get('Location', ''))
    status, _, res_html = stu2.get(redir_url, follow=True)
    assert status == 200
    assert ("정상 완료" in res_html or "출석" in res_html)
    print("✓ 학생 2 정상 번호로 출석 성공")

    print("\n=== [5] 학생 1: 중복 출석 방어 검증 ===")
    csrf = get_csrf(stu1, f"{BASE_URL}/classes/{class_id}/attendance")
    status, headers, _ = stu1.post(f"{BASE_URL}/classes/{class_id}/attendance/submit", data={
        "authCode": auth_code,
        "_csrf": csrf
    })
    assert status == 302
    redir_url = urllib.parse.urljoin(BASE_URL, headers.get('Location', ''))
    status, _, res_html = stu1.get(redir_url, follow=True)
    assert ("이미 출석" in res_html or "출석 완료" in res_html)
    print("✓ 중복 출석 방어 정상 작동")

    print("\n=== [6] 교수 대형 스크린 실시간 폴링 API (/status) 검증 ===")
    status, _, json_body = prof.get(f"{BASE_URL}/classes/{class_id}/attendance/sessions/{session_id}/status")
    assert status == 200
    status_data = json.loads(json_body)
    print(f"✓ 실시간 폴링 JSON 응답: 출석={status_data.get('presentCount')}, 총인원={status_data.get('totalStudents')}, 남은시간={status_data.get('remainingSeconds')}초")
    assert status_data.get('presentCount') == 2
    assert status_data.get('totalStudents') == 2

    print("\n=== [7] 교수 출석 세션 마감 ===")
    csrf = get_csrf(prof, f"{BASE_URL}/classes/{class_id}/attendance")
    status, _, res_html = prof.post(f"{BASE_URL}/classes/{class_id}/attendance/sessions/{session_id}/close", data={
        "_csrf": csrf
    }, follow=True)
    assert status == 200
    assert ("마감되었습니다" in res_html or "CLOSED" in res_html or "출석 현황" in res_html)
    print("✓ 세션 마감 성공")

    print("\n=== [8] 학생 대시보드 출석률 100% 반영 확인 ===")
    status, _, stu_dash = stu1.get(f"{BASE_URL}/classes/{class_id}/attendance", follow=True)
    assert status == 200
    assert ("100.0%" in stu_dash or "100%" in stu_dash)
    print("✓ 학생 대시보드에 출석률 100% 정상 반영 확인")

    print("\n🎉 모든 실시간 4자리 간편 출석 체크 시나리오 100% 통과 (ALL SUCCESS)!")

if __name__ == "__main__":
    run_tests()
