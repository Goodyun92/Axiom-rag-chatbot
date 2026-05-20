# Axiom (PDF RAG System)

Spring Boot 3.4+, Spring AI, PostgreSQL(pgvector), 그리고 Tailwind CSS(Premium UI)를 활용하여 구축된 로컬 전용 PDF 기반 검색 증강 생성(RAG) 시스템입니다.

## 사전 요구사항
* Docker Desktop (또는 Docker Engine & Docker Compose)
* Java 21 JDK
* OpenAI API Key

---

## 🚀 로컬 실행 가이드

### 1단계: PostgreSQL (pgvector) 컨테이너 실행
프로젝트 루트 디렉토리에서 아래 명령어를 실행하여 pgvector가 포함된 데이터베이스를 백그라운드에서 실행합니다.

```bash
docker compose up -d
```
> `docker-compose.yml` 설정에 의해 `spring_ai_db` 데이터베이스와 기본 계정이 자동 생성됩니다.

### 2단계: vector 확장 모듈 활성화
스프링 애플리케이션의 설정으로 자동 생성되지만, 최초 구동 시에는 컨테이너에 접속하여 확장을 생성해주는 것이 안전합니다.

**터미널에서 한 줄 명령어로 실행:**
```bash
docker exec -it spring_ai_pgvector psql -U myuser -d spring_ai_db -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 3단계: 환경 변수 설정 및 애플리케이션 실행
프로젝트 루트 디렉토리에 `.env` 파일을 생성하고 아래와 같이 발급받은 API Key 및 DB 정보를 작성합니다.

**`.env` 파일 생성 (`c:\workspace\rag\.env`)**
```properties
OPENAI_API_KEY=sk-당신의-api-키
DB_USER=myuser
DB_PASSWORD=mypassword
DB_NAME=spring_ai_db
```

파일 작성이 완료되었다면, 아래 명령어로 애플리케이션을 구동합니다.

```bash
./gradlew bootRun
```

### 4단계: 시스템 접속
애플리케이션이 정상적으로 시작되면, 브라우저를 열고 아래 주소로 접속합니다.
* **Axiom - 문서관리:** [http://localhost:8080/documents](http://localhost:8080/documents)
* **Axiom - 채팅:** [http://localhost:8080/chat](http://localhost:8080/chat)

---

## 🏗 프로젝트 구조 (Architecture)

프로젝트는 명확한 관심사 분리(SoC)를 기반으로 다음과 같이 구성되어 있습니다.

```text
src/main/
├── java/org/dyheo/rag/
│   ├── controller/
│   │   └── WebController.java      # 화면(HTML) 라우팅, 파일 업로드/삭제, 채팅 API 엔드포인트(HTTP 요청) 처리
│   ├── service/
│   │   ├── DocumentService.java    # PDF 문서 파싱(PagePdfDocumentReader), 청크 분할(TokenTextSplitter) 및 Vector DB 저장
│   │   └── RagChatService.java     # Vector DB 유사도 검색, 프롬프트 조립, OpenAI API 호출 및 응답(ChatClient) 처리 로직
│   ├── dto/
│   │   └── ChatResponse.java       # 프론트엔드로 전달할 AI 응답 텍스트와 참조 출처(Sources) 데이터를 담는 객체
│   └── RagApplication.java         # Spring Boot 애플리케이션 시작점
└── resources/
    ├── application.yml             # DB 연결 정보, Spring AI 설정(모델, pgvector HNSW 인덱스 등)
    └── templates/                  # Thymeleaf + Tailwind CSS + jQuery 기반의 프론트엔드 화면
        ├── documents.html          # 지식 베이스(Vector DB) 문서 업로드 및 관리(삭제) UI
        └── chat.html               # 실시간 AI 질의응답, 참조 문서 뱃지 표시, sessionStorage 기반 대화 내역 유지 UI
```

---

## 🌟 주요 기능 및 최적화 사항

### 1. 고도화된 RAG 유사도 검색 로직 (Threshold 필터링)
* **Similarity Threshold (0.50):** 단순히 거리가 가장 가까운 N개의 문서를 무조건 가져오는 것이 아니라, **유사도 점수가 일정 기준(0.50) 이상인 의미 있는 문맥만 추출**하도록 설정되어 있습니다. (한국어 임베딩 점수 분포를 고려하여 실효성 있게 조정한 값입니다.)
* **조기 리턴 (Early Return):** 검색 결과 관련성 있는 문서가 단 하나도 매칭되지 않을 경우, 불필요한 **OpenAI API 호출을 생략(Skip)**하고 즉시 "관련된 정보를 찾을 수 없습니다"라고 응답합니다. 이를 통해 **API 토큰 비용 및 응답 시간(Latency)을 획기적으로 절약**하고 환각(Hallucination)을 방지합니다.

### 2. 효율적인 참조 문서(Source) 시각화 및 정렬
* **유사도 최상위 기준 정렬:** 참조하는 문서가 여러 개일 경우, 각 문서별 가장 매칭도가 높은 페이지의 유사도 점수(가장 짧은 거리)를 기준으로 내림차순 정렬하여 최우선 참조 문서가 가장 위에 나옵니다.
* **한 줄에 하나씩 출력 (줄 바꿈):** 문서 출처가 가로로 난잡하게 표시되지 않도록 세로 정렬(`flex-col`)을 적용하여 한 눈에 알아보기 쉽게 정렬했습니다.
* **유사도 등급(Tier)별 직관적 색상 표시**:
  - **Tier 1 (매우 높음)**: 쨍하고 선명한 **초록색** 뱃지
  - **Tier 2 (높음)**: 톤 다운된 **탁한 초록색** 뱃지
  - **Tier 3 (보통)**: 녹색이 빠진 **차분한 회색** 뱃지
  - 사용자는 색상만으로 어떤 문서가 내 질문과 가장 밀접하게 연결되었는지 쉽게 판단할 수 있습니다.

### 3. 세션 기반 채팅 내역 유지 (Stateful UI)
* 전통적인 SSR(Server-Side Rendering) 방식의 한계를 보완하기 위해 브라우저의 `sessionStorage`를 활용했습니다.
* 문서 관리 페이지 등 다른 화면으로 이동(Navigating)했다가 돌아오더라도, 새로고침 없이 **기존 채팅 내역과 스크롤 위치가 그대로 복원**되어 부드러운 사용자 경험을 제공합니다.

### 4. 강력한 모니터링 및 로깅 체계 (Observability)
* **문서 관리 로깅:** 문서 추가 및 삭제 시 처리 결과(성공/실패/오류)와 함께 어떤 파일인지 명확하게 `[INFO]` 로그로 남깁니다.
* **유사도 검색 로깅:** AI에게 프롬프트를 넘기기 전, Vector DB가 대체 어떤 문서를 가져왔는지 파악할 수 있도록 거리(Distance)와 텍스트 프리뷰(50자)를 로깅합니다.
* **AI API Request 로깅:** 실제 OpenAI로 날아가는 최종 프롬프트 및 시스템 지시문을 터미널에서 확인할 수 있습니다. (로그 창이 더러워지는 것을 방지하기 위해 컨텍스트 내용은 20자 내외로 축약하여 출력합니다.)

### 5. 프리미엄 UI/UX 및 편의성
* Tailwind CSS와 Glassmorphism(유리 질감)을 적용한 다크 테마 기반의 반응형 인터페이스.
* 입력창 크기 자동 조절 및 스크롤 하단 여백(`pb-56`) 처리를 통해 긴 질문 작성 시에도 채팅 내역이 가려지지 않는 안정적인 뷰포트 제공.
* 답변 생성 대기 상태를 나타내는 로딩 표시 박스를 화면 중앙에 배치하여 대기 시인성을 직관적으로 확보했습니다.
