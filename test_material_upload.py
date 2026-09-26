import urllib.request
import urllib.parse
import http.cookiejar
from html.parser import HTMLParser
import uuid

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

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def test_upload_and_download():
    # 1. Login as instructor
    jar_prof = http.cookiejar.CookieJar()
    opener_prof = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar_prof), NoRedirectHandler())
    
    csrf, _ = get_csrf(opener_prof, "http://localhost:8080/login")
    post_data = urllib.parse.urlencode({
        "email": "prof_test@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf
    }).encode('utf-8')
    
    # Register prof first
    csrf_reg, _ = get_csrf(opener_prof, "http://localhost:8080/register")
    reg_prof = urllib.parse.urlencode({
        "role": "ROLE_INSTRUCTOR",
        "name": "강교수",
        "studentNo": "PROF999",
        "email": "prof_test@univ.ac.kr",
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf_reg
    }).encode('utf-8')
    try:
        opener_prof.open(urllib.request.Request("http://localhost:8080/register", data=reg_prof))
    except urllib.error.HTTPError as e:
        if e.code != 302:
            raise e
    
    # Login prof
    csrf_login, _ = get_csrf(opener_prof, "http://localhost:8080/login")
    try:
        opener_prof.open(urllib.request.Request("http://localhost:8080/login", data=urllib.parse.urlencode({
            "email": "prof_test@univ.ac.kr",
            "password": "password123",
            "_csrf": csrf_login
        }).encode('utf-8')))
    except urllib.error.HTTPError as e:
        if e.code != 302:
            raise e
    
    # Create class
    csrf_class, _ = get_csrf(opener_prof, "http://localhost:8080/classes/new")
    create_class = urllib.parse.urlencode({
        "name": "인공지능 개론",
        "courseCode": "AI101",
        "semester": "2026-1학기",
        "division": "01",
        "description": "AI 수업입니다.",
        "_csrf": csrf_class
    }).encode('utf-8')
    try:
        res = opener_prof.open(urllib.request.Request("http://localhost:8080/classes/new", data=create_class))
        loc = res.headers.get("Location")
    except urllib.error.HTTPError as e:
        loc = e.headers.get("Location")
    class_id = loc.split("/classes/")[1].split("?")[0]
    print(f"[TEST] Created class id: {class_id}")
    
    # Upload Material
    csrf_mat, _ = get_csrf(opener_prof, f"http://localhost:8080/classes/{class_id}")
    fields = {
        "weekNumber": "1",
        "title": "1주차 오리엔테이션 슬라이드",
        "description": "첫 수업 자료입니다.",
        "published": "true",
        "_csrf": csrf_mat
    }
    sample_pdf_bytes = b"%PDF-1.4 Mock PDF Content For Testing"
    files = {
        "file": ("week01_intro.pdf", sample_pdf_bytes, "application/pdf")
    }
    
    code, headers, html = post_multipart(opener_prof, f"http://localhost:8080/classes/{class_id}/materials", fields, files)
    print(f"[TEST] Upload response code: {code}, redirect: {headers.get('Location')}")
    
    # Check detail page has the material
    req_detail = urllib.request.Request(f"http://localhost:8080/classes/{class_id}")
    res_detail = opener_prof.open(req_detail)
    html_detail = res_detail.read().decode('utf-8')
    if "1주차 오리엔테이션 슬라이드" in html_detail and "week01_intro.pdf" in html_detail:
        print("[TEST SUCCESS] Uploaded material visible on classroom page!")
    else:
        print("[TEST FAILED] Material not visible on page")

if __name__ == "__main__":
    test_upload_and_download()
