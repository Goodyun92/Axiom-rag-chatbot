# Axiom (PDF업로드 RAG 기반 ChatBot System)

<table width="100%">
  <tr>
    <td width="50%" align="center">
      <b>📄 문서 관리 페이지</b><br><br>
      <img src="https://github.com/user-attachments/assets/7d5249f0-947f-43d8-a672-9c2a946c77ae" width="100%" alt="docbase-page" />
    </td>
    <td width="50%" align="center">
      <b>💬 채팅 페이지</b><br><br>
      <img src="https://github.com/user-attachments/assets/2c0ce910-3d38-4fbc-92bd-43979d5c370c" width="100%" alt="chat-page" />
    </td>
  </tr>
</table>

<a href="https://axiom-rag-chatbot.onrender.com" target="_blank">🔗 접속하기</a>

---

## 개요 및 사용 기술
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
│   │   ├── DocumentService.java    # PDF 문서 파싱(TikaDocumentReader), 청크 분할(TokenTextSplitter) 및 Vector DB 저장
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
* **Similarity Threshold (0.50):** 단순히 거리가 가장 가까운 N개의 문서를 가져오는 것이 아니라, **유사도 점수가 일정 기준(0.50) 이하(거리 기준)인 의미 있는 문맥만 추출**하도록 설정되어 있습니다. (한국어 임베딩 점수 분포를 고려하여 실효성 있게 조정한 값입니다.)
* **조기 리턴 (Early Return):** 검색 결과 관련성 있는 문서가 단 하나도 매칭되지 않을 경우, 불필요한 **OpenAI API 호출을 생략(Skip)**하고 즉시 "관련된 정보를 찾을 수 없습니다"라고 응답합니다. 이를 통해 **API 토큰 비용 및 응답 시간(Latency)을 획기적으로 절약**하고 환각(Hallucination)을 방지합니다.
* **정밀한 답변 누락 감지 (E999 코드 기반):** AI가 프롬프트 규칙에 따라 관련 정보가 없을 시 `E999` 코드만을 반환하게끔 유도하여, 잘못된 부정형 답변(예: "문서에 없습니다")을 백엔드에서 100% 확실하게 필터링합니다.

### 2. 효율적인 참조 문서(Source) 시각화 및 정렬
* **유사도 최상위 기준 정렬:** 각 문서별 가장 매칭도가 높은 페이지의 유사도 점수(가장 짧은 거리)를 기준으로 내림차순 정렬하여 최우선 참조 문서가 상단에 노출됩니다.
* **유사도 등급(Tier)별 직관적 색상 점(Dot) 표시**:
  - **Tier 1 (연관도 매우 높음)**: **초록색 점** (거리 <= 0.38)
  - **Tier 2 (연관도 높음)**: **연초록색 점** (거리 <= 0.45)
  - **Tier 3 (연관도 보통)**: **회색 점**
  - 사용자는 "참조 문서" 상단의 **미니 범례(Legend)** 와 각 문서명 앞의 **색상 점(Dot)** 만으로 직관적인 신뢰도 판단이 가능합니다. 불필요한 뱃지 배경색과 텍스트를 제거하여 모바일 화면에서도 미니멀하고 세련된 UI를 제공합니다.

### 3. 세션 기반 채팅 내역 유지 (Stateful UI)
* 전통적인 SSR(Server-Side Rendering) 방식의 한계를 보완하기 위해 브라우저의 `sessionStorage`를 활용했습니다.
* 문서 관리 페이지 등 다른 화면으로 이동(Navigating)했다가 돌아오더라도, 새로고침 없이 **기존 채팅 내역과 스크롤 위치가 그대로 복원**되어 부드러운 사용자 경험을 제공합니다.

### 4. 강력한 모니터링 및 로깅 체계 (Observability)
* **문서 관리 로깅:** 문서 추가/삭제 시 처리 결과와 함께 파일명을 `[INFO]` 로그로 남깁니다.
* **유사도 검색 로깅:** AI에게 프롬프트를 넘기기 전, Vector DB가 가져온 문서들의 거리(Distance)와 텍스트 프리뷰(50자)를 로깅합니다.
* **AI API Request 로깅:** 최종 프롬프트 및 시스템 지시문을 확인 가능하도록 로깅(문맥 축약)하여 콘솔 출력을 제공합니다.

### 5. 프리미엄 UI/UX 및 완벽한 모바일 반응형
* Tailwind CSS와 Glassmorphism(유리 질감)을 적용한 다크 테마 기반의 반응형 인터페이스.
* **모바일 최적화 (Responsive Design):** 뷰포트 확대 오류를 해결하는 메타 태그를 적용했으며, 모바일 가상 키보드 팝업 시 화면이 겹치거나 깨지지 않도록 `flex-col` 구조와 모바일 표준 높이(`h-dvh`) 속성을 완벽히 구현했습니다.
* 테이블 UI에서 긴 파일명이 레이아웃을 망가뜨리지 않도록 텍스트 말줄임(Truncate) 처리 및 여백(`padding`) 최적화를 거쳤습니다.

### 6. 안정적인 PDF 문서 파싱 파이프라인
* 목차(TOC)가 없거나 비표준 폰트를 사용하는 PDF 파일도 안정적으로 파싱하기 위해, 업계 표준인 아파치 타카(Apache Tika) 기반의 **`TikaDocumentReader`** 엔진을 채택하여 문서 파싱 호환성과 강건성을 극대화했습니다.
