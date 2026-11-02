# StoryDebate AI

**작품 자료 기반 AI 토론 웹 서비스**

## 1. 프로젝트 소개

StoryDebate AI는 사용자가 문학 작품 및 이야기의 내용에 관해 질문하고 AI와 토론할 수 있도록 지원하는 웹 서비스입니다.

Ollama 기반의 로컬 AI 모델과 RAG(Retrieval-Augmented Generation, 검색 증강 생성) 기술을 활용하여 작품 관련 자료를 검색하고, 검색 결과를 바탕으로 AI가 답변을 생성하는 것을 목표로 합니다.

단순히 AI의 답변을 제공하는 것을 넘어, 답변에 활용한 참고 자료도 함께 제공하여 사용자가 답변의 근거를 확인할 수 있도록 설계합니다.

## 2. 프로젝트 목표

- 작품에 관한 질문과 AI 토론 기능 구현
- 작품 자료를 기반으로 한 답변 생성
- RAG를 활용한 관련 자료 검색
- AI 답변과 참고 자료 제공
- 로컬 AI 모델을 활용한 독립적인 AI 서비스 구축

## 3. 주요 기능

### 구현한 백엔드 기능
- Spring Boot 기반 REST API 구성
- AI 서버 상태 확인 API
- Ollama 로컬 AI 모델 연동
- AI 채팅 및 한국어 답변 생성
- PostgreSQL 데이터베이스 연결
- pgvector 기반 벡터 저장 환경 구성
- 작품 자료 등록 및 임베딩 저장
- 질문과 관련된 작품 자료 검색

### 개발 및 개선 예정 기능
- RAG 검색 결과를 활용한 답변 품질 개선
- AI 답변과 참고 자료의 연동 및 검증
- React 기반 채팅 UI 구현 및 백엔드 연동
- 사용자 편의성을 고려한 UI·UX 개선
- 전체 기능 통합 테스트 및 오류 수정

## 4. 기술 스택

### Backend
- Java 21
- Spring Boot
- Gradle
- REST API

### Frontend
- React
- JavaScript
- HTML / CSS

### AI
- Ollama
- Qwen 2.5 3B: 답변 생성
- nomic-embed-text: 텍스트 임베딩 생성
- RAG: 검색 증강 생성

### Database
- PostgreSQL
- pgvector

### Development Tools
- IntelliJ IDEA
- Git
- GitHub

## 5. 시스템 구조

1. 사용자가 웹 화면에서 작품에 관한 질문을 입력합니다.
2. Spring Boot 백엔드가 질문을 전달받습니다.
3. 질문의 임베딩을 생성하고 PostgreSQL의 pgvector를 이용해 관련 작품 자료를 검색합니다.
4. 검색된 자료와 대화 맥락을 Ollama의 AI 모델에 전달합니다.
5. AI 모델이 자료를 참고하여 답변을 생성합니다.
6. 백엔드는 답변과 참고 자료를 반환하고 프론트엔드는 이를 화면에 표시합니다.

## 6. 데이터베이스

PostgreSQL과 pgvector를 사용하여 작품 관련 자료와 임베딩 데이터를 저장하고 검색합니다.

주요 데이터 항목:
- 작품 제목
- 자료 제목
- 자료 구분 및 섹션
- 작품 관련 본문
- 텍스트 임베딩
- 생성 시각

## 7. 주요 API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/health` | 백엔드 상태 확인 |
| GET | `/api/db/health` | 데이터베이스 연결 확인 |
| POST | `/api/chat` | AI 채팅 요청 |
| POST | `/api/knowledge` | 작품 자료 등록 |
| POST | `/api/knowledge/search` | 관련 작품 자료 검색 |

※ API의 실제 요청 및 응답 형식은 백엔드 구현에 맞춰 업데이트합니다.

## 8. 실행 환경

필요한 프로그램:
- Java 21
- PostgreSQL 및 pgvector
- Ollama
- Qwen 2.5 3B 모델
- nomic-embed-text 모델
- Node.js 및 npm (프론트엔드 실행 시)

### AI 모델 설치

```bash
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
```

Ollama를 실행한 후 백엔드 설정에서 Ollama 서버 주소와 모델 이름을 확인합니다.

### Backend 실행

Windows 환경에서 백엔드 프로젝트 폴더로 이동한 뒤 실행합니다.

```powershell
.\gradlew.bat bootRun
```

기본 주소: `http://localhost:8080`

데이터베이스 접속 정보는 로컬 환경에 맞게 설정해야 합니다.

### Frontend 실행

프론트엔드 프로젝트 폴더에서 다음 명령어를 실행합니다.

```bash
npm install
npm run dev
```

※ 프론트엔드 프로젝트 구성에 따라 실행 명령어는 달라질 수 있습니다.

## 9. 향후 개선 방향

- 작품별 대화 기록 관리
- 작품 및 참고 자료 관리 화면
- 검색 결과의 정확도 개선
- 근거가 부족한 질문에 대한 안내
- AI 답변 품질 및 응답 시간 최적화
- 사용자 편의성을 고려한 UI·UX 개선

## 10. 프로젝트 진행 상황

현재 로컬 AI 모델과 Spring Boot 백엔드 연동, PostgreSQL 및 pgvector 설정, 작품 자료 저장과 검색 기능을 중심으로 개발을 진행하고 있습니다.

향후 RAG 기반 답변 기능을 검증하고 프론트엔드를 연동하여, 사용자가 작품에 관해 질문하고 참고 자료를 확인할 수 있는 서비스를 완성할 예정입니다.
