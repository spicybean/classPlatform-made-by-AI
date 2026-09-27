import urllib.request
import urllib.parse
import http.cookiejar
from html.parser import HTMLParser
import re

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
    try:
        response = opener.open(req)
        html = response.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        html = e.read().decode('utf-8')
    parser = CSRFParser()
    parser.feed(html)
    return parser.csrf, html

def post_data(opener, url, data_dict):
    data = urllib.parse.urlencode(data_dict).encode('utf-8')
    req = urllib.request.Request(url, data=data)
    try:
        res = opener.open(req)
        return res.status, res.headers, res.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read().decode('utf-8')

# We need a NoRedirectHandler to catch 302s manually to verify them
class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def test_full_flow():
    cookie_jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar), NoRedirectHandler())

    print("--- [1] Instructor Registration ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/register")
    inst_data = {
        "role": "ROLE_INSTRUCTOR",
        "name": "아이작 뉴턴",
        "studentNo": "PROF1001",
        "email": "isaac@univ.ac.kr",
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf_token
    }
    code, headers, _ = post_data(opener, "http://localhost:8080/register", inst_data)
    if code != 302 or "login" not in headers.get("Location", ""):
        print(f"FAILED Instructor Registration: {code} {headers.get('Location')}")
        return
    print("SUCCESS: Instructor Registration")

    print("--- [2] Instructor Login ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/login")
    login_data = {
        "email": "isaac@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf_token
    }
    code, headers, _ = post_data(opener, "http://localhost:8080/login", login_data)
    if code != 302 or headers.get("Location") != "http://localhost:8080/":
        print(f"FAILED Instructor Login: {code} {headers.get('Location')}")
        return
    print("SUCCESS: Instructor Login")

    print("--- [3] Create ClassRoom ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/classes/new")
    class_data = {
        "name": "Advanced Physics 101",
        "courseCode": "PHY101",
        "semester": "2026-1",
        "division": "01",
        "description": "Gravity and beyond",
        "_csrf": csrf_token
    }
    code, headers, _ = post_data(opener, "http://localhost:8080/classes/new", class_data)
    if code != 302 or "/classes/" not in headers.get("Location", ""):
        print(f"FAILED Create ClassRoom: {code} {headers.get('Location')}")
        return
    class_url = headers.get("Location")
    print(f"SUCCESS: Create ClassRoom, redirected to {class_url}")
    
    # Follow redirect to get invite code
    code, headers, html = post_data(opener, class_url, {}) # wait, GET redirect
    # Need to do a normal GET to class_url
    opener_redirect = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))
    res = opener_redirect.open(class_url)
    html = res.read().decode('utf-8')
    
    # Extract invite code
    match = re.search(r'class="font-mono[^>]*>\s*([A-Z0-9]{6})\s*</span>', html)
    if not match:
        print("FAILED: Could not find invite code in class page HTML")
        return
    invite_code = match.group(1)
    print(f"SUCCESS: Found Invite Code: {invite_code}")

    print("--- [4] Instructor Logout ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/")
    code, headers, _ = post_data(opener, "http://localhost:8080/logout", {"_csrf": csrf_token})
    if code != 302 or "logout=true" not in headers.get("Location", ""):
        print(f"FAILED Instructor Logout: {code}")
        return
    print("SUCCESS: Instructor Logout")

    print("--- [5] Student Registration ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/register")
    stud_data = {
        "role": "ROLE_STUDENT",
        "name": "알베르트 아인슈타인",
        "studentNo": "20260001",
        "email": "albert@univ.ac.kr",
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf_token
    }
    code, headers, _ = post_data(opener, "http://localhost:8080/register", stud_data)
    if code != 302:
        print("FAILED Student Registration")
        return
    print("SUCCESS: Student Registration")

    print("--- [6] Student Login ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/login")
    code, headers, _ = post_data(opener, "http://localhost:8080/login", {
        "email": "albert@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf_token
    })
    if code != 302 or headers.get("Location") != "http://localhost:8080/":
        print("FAILED Student Login")
        return
    print("SUCCESS: Student Login")

    print("--- [7] Student Join ClassRoom ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/classes/join")
    join_data = {
        "inviteCode": invite_code,
        "_csrf": csrf_token
    }
    code, headers, _ = post_data(opener, "http://localhost:8080/classes/join", join_data)
    if code != 302 or "/classes/" not in headers.get("Location", ""):
        print(f"FAILED Join ClassRoom: {code} {headers.get('Location')}")
        return
    joined_class_url = headers.get("Location")
    print(f"SUCCESS: Join ClassRoom, redirected to {joined_class_url}")

    print("--- [8] Student View ClassRoom ---")
    res = opener_redirect.open(joined_class_url)
    if res.status == 200:
        print("SUCCESS: Student can view ClassRoom page")
    else:
        print("FAILED: View ClassRoom status", res.status)

    print("=== ALL TESTS PASSED ===")

if __name__ == "__main__":
    test_full_flow()
