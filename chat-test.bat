
@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo.
echo ===== StoryDebate AI 대화 테스트 =====
echo.

echo [1단계] 첫 번째 질문을 보내는 중...

curl.exe -s -X POST "http://localhost:8080/api/chat" ^
 -H "Content-Type: application/json; charset=utf-8" ^
 --data-binary "@chat1.json" ^
 -o answer1.json

if errorlevel 1 (
    echo 첫 번째 요청에 실패했습니다.
    pause
    exit /b 1
)

echo 첫 번째 답변이 answer1.json에 저장되었습니다.
echo.

echo [2단계] 두 번째 질문을 준비합니다...

python -c "import json; a=json.load(open('answer1.json',encoding='utf-8')); b={'question':'하지만 다른 방법이 없었다면 어떨까?','history':[{'role':'user','content':a['question']},{'role':'assistant','content':a['answer']}]}; json.dump(b,open('chat2-auto.json','w',encoding='utf-8'),ensure_ascii=False)" 

if errorlevel 1 (
    echo 대화 기록 생성에 실패했습니다.
    pause
    exit /b 1
)

echo 실제 첫 번째 AI 답변을 대화 기록에 연결했습니다.
echo.

echo [3단계] 두 번째 질문을 보내는 중...

curl.exe -s -X POST "http://localhost:8080/api/chat" ^
 -H "Content-Type: application/json; charset=utf-8" ^
 --data-binary "@chat2-auto.json"

echo.
echo.
echo ===== 테스트 완료 =====
pause
