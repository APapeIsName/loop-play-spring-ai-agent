# .private/ — 로컬 전용 작업 기록 (git 미추적)

이 폴더 전체는 `.gitignore` 처리되어 git에 올라가지 않는다.
정제된 회고/개인 의견은 루트 `JOURNAL.md` 로 옮겨 기록한다.

## 구조

- `missions/` — 사용자가 제공한 **주차별 과제 원문**.
  파일명 규칙: `week{N}.md` (예: `week1.md`)
- `notes/` — 작업 메모, 플랜, 중간 산출물.

## 주차별 과제 파일 템플릿 (`missions/week{N}.md`)

```markdown
# Week {N} 과제

## 과제 원문
(사용자가 제공한 내용 그대로)

## 요구사항 분석
-

## 작업 계획
-
```
