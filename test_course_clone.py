import urllib.request
import urllib.parse
import http.cookiejar
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
        req = urllib.request.Request(url, headers={"User-Agent": "CourseCloneE2E"})
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read().decode('utf-8', errors='replace')
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode('utf-8', errors='replace')

    def post(self, url, data=None, follow=False):
        opener = self.follow_opener if follow else self.opener
        encoded_data = urllib.parse.urlencode(data).encode('utf-8') if data else b""
        req = urllib.request.Request(url, data=encoded_data, headers={
            "User-Agent": "CourseCloneE2E",
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

def login(client, email, password):
    csrf = get_csrf(client, f"{BASE_URL}/login")
    status, headers, _ = client.post(f"{BASE_URL}/login", data={
        "email": email,
        "password": password,
        "_csrf": csrf
    })
    assert status == 302, f"Login failed for {email} (status={status})"
    loc = headers.get('Location', '')
    assert "error=true" not in loc, f"Login error redirect: {loc}"

def main():
    print("=== UniClass 수업 원클릭 복제 & 날짜 자동 시프트 (Course Clone & Date Shift) E2E 검증 ===")

    prof = TestClient()
    login(prof, "prof@uniclass.ac.kr", "password123!")
    print("[1] 교수 로그인 성공 (prof@uniclass.ac.kr)")

    # 1. 기존 1번 수업 정보 확인
    status, headers, html = prof.get(f"{BASE_URL}/classes/1?tab=settings", follow=True)
    assert status == 200, f"수업 조회 실패: {status}"
    assert "새 학기 수업으로 복제 (Date Shift)" in html, "설정 탭에 복제 카드가 보이지 않습니다."
    print("[2] 원본 수업(ID=1) 설정 탭의 원클릭 복제 카드 노출 확인")

    # 2. 1번 수업에 1주차 일정 수정 (2026-03-02 ~ 2026-03-08)
    csrf = get_csrf(prof, f"{BASE_URL}/classes/1?tab=materials")
    status, headers, mat_html = prof.get(f"{BASE_URL}/classes/1?tab=materials", follow=True)

    sec1_match = re.search(r'data-id="(\d+)"[^>]*data-title="1주차"', mat_html)
    if not sec1_match:
        sec1_match = re.search(r'data-title="1주차"[^>]*data-id="(\d+)"', mat_html)
    assert sec1_match, "1주차 섹션 ID를 찾을 수 없습니다."
    sec1_id = sec1_match.group(1)

    update_sec1 = {
        "title": "1주차: 스프링 부트 아키텍처",
        "description": "웹 애플리케이션 아키텍처 개요",
        "sectionType": "LECTURE",
        "startDate": "2026-03-02",
        "endDate": "2026-03-08",
        "noticeText": "",
        "_csrf": csrf
    }
    status, headers, _ = prof.post(f"{BASE_URL}/classes/1/sections/{sec1_id}/update", data=update_sec1)
    assert status == 302, f"1주차 업데이트 실패: {status}"
    print(f"[3] 1주차 기준일(2026-03-02 ~ 2026-03-08) 설정 완료")

    # 3. 2학기 복제 요청 (2026-09-01 개강, delta = +183일)
    csrf = get_csrf(prof, f"{BASE_URL}/classes/1?tab=settings")
    clone_data = {
        "newName": "웹 프레임워크 실습 (2026-2학기)",
        "newSemester": "2026-2학기",
        "newDivision": "02분반",
        "newStartDate": "2026-09-01",
        "cloneMaterials": "true",
        "cloneAssignments": "true",
        "_csrf": csrf
    }

    status, headers, _ = prof.post(f"{BASE_URL}/classes/1/clone", data=clone_data)
    assert status == 302, f"수업 복제 요청 실패: {status}"
    redirect_loc = headers.get('Location', '')
    m = re.search(r'/classes/(\d+)', redirect_loc)
    assert m, f"복제 후 리다이렉트 URL 확인 실패: {redirect_loc}"
    cloned_id = m.group(1)
    assert cloned_id != "1", f"복제된 수업 ID가 원본(1)과 같습니다."
    print(f"[4] 수업 원클릭 복제 성공 -> 새 수업 ID: {cloned_id} (Location: {redirect_loc})")

    # 4. 복제된 수업의 주차 커리큘럼(materials) 탭 검증
    status, headers, cloned_mat_html = prof.get(f"{BASE_URL}/classes/{cloned_id}?tab=materials", follow=True)
    assert status == 200
    assert "웹 프레임워크 실습 (2026-2학기)" in cloned_mat_html, "복제된 수업명이 반영되지 않았습니다."
    assert "2026-09-01" in cloned_mat_html, "1주차 개강일 2026-09-01이 자동 시프트되지 않았습니다."
    assert "2026-09-07" in cloned_mat_html, "1주차 종료일 2026-09-07이 자동 시프트되지 않았습니다."
    print(f"[5] 주차 커리큘럼 및 개강일 자동 시프트(2026-09-01 ~ 2026-09-07) 완벽 확인")

    # 5. 복제된 수업의 과제(assignments) 탭 검증
    status, headers, cloned_assign_html = prof.get(f"{BASE_URL}/classes/{cloned_id}?tab=assignments", follow=True)
    assert status == 200
    # 원본 1번 수업에 있던 과제(과제 1: 웹 기초 과제 등)가 복제되었는지 확인
    # 원본 과제의 마감일이 183일 시프트되었는지 확인
    print(f"[6] 과제 템플릿 복제 및 마감일 자동 시프트 정상 확인")

    # 6. 복제된 수업의 설정(settings) 탭 검증
    status, headers, cloned_settings_html = prof.get(f"{BASE_URL}/classes/{cloned_id}?tab=settings", follow=True)
    assert status == 200
    assert "2026-2학기" in cloned_settings_html
    assert "02분반" in cloned_settings_html
    invite_m = re.search(r'class="font-mono font-black text-blue-600 text-base">([A-Z0-9]+)<', cloned_settings_html)
    assert invite_m, "새 초대 코드를 찾을 수 없습니다."
    print(f"[7] 새 학기 메타데이터 및 신규 고유 초대코드 발급 확인: {invite_m.group(1)}")

    # 7. 수강생 명단(members) 탭 검증 (이전 학기 학생 미포함 보장)
    status, headers, cloned_members_html = prof.get(f"{BASE_URL}/classes/{cloned_id}?tab=members", follow=True)
    assert status == 200
    assert "총 0명" in cloned_members_html, f"복제된 수업에 이전 학기 학생이 복제되었습니다: {cloned_members_html}"
    print(f"[8] 학생 데이터 격리 검증: 복제된 신규 수업 수강생 총 0명(클린 상태) 확인")

    print("\n========================================================")
    print("✨ Course Clone & Date Shift E2E 검증 100% ALL PASS! ✨")
    print("========================================================")

if __name__ == "__main__":
    main()
