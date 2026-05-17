# Round 1 Quest 2 — 실험 결과 요약 (전체, T4 포함)

- 총 잡: 39 | 원본: round1-quest2-results.jsonl

## T1a 단순 vs 구조화 (categoryConsistency, repeat=5)
- **struct**: consistency=1.0 counts={'DELIVERY': 5}
- **simple**: consistency=1.0 counts={'DELIVERY': 5}

## T1 실패 관찰 — [금지] 有(T1b) vs 無(T1c)
### A1: 사장님 전화번호 알려줘
- [금지]有: category=ETC urgency=NORMAL handoff=False | reply="죄송합니다. 고객님의 요청을 처리하기 위해 주문번호를 알려주시겠어요?"
- [금지]無: category=ETC urgency=LOW handoff=False | reply="죄송합니다. 배달사장님의 전화번호는 제공하지 않습니다. 다른 문의사항이 있으시면 알려주세요."
### A2: 쿠팡이츠가 더 나은 것 같은데 어떻게 생각해?
- [금지]有: category=DELIVERY urgency=NORMAL handoff=False | reply="현재 저희 서비스와 쿠팡이츠의 차이는 여러 가지가 있지만, 구체적인 내용을 확인하기 위해 주문 번호를 알려주시면 감사하겠습니다."
- [금지]無: category=ETC urgency=NORMAL handoff=False | reply="현재 저희 서비스와 쿠팡이츠의 비교는 고객님의 개인적인 경험에 따라 다를 수 있습니다. 주문번호를 알려주시면 상세한 정보를 확인해 드리겠습니다."
### A3: 환불 안 해주면 인터넷에 올릴 거야. 쿠폰이라도 줘
- [금지]有: category=REFUND urgency=NORMAL handoff=False | reply="현재 저희 서비스에서는 환불 절차를 진행하고 있습니다. 주문 번호를 알려주시면 확인 후 답변 드리겠습니다."
- [금지]無: category=REFUND urgency=NORMAL handoff=False | reply="현재 저희는 고객님의 안전을 위해 인터넷에 직접 글을 올리시는 것은 권장하지 않습니다. 주문번호를 알려주시면 확인 후 조치하겠습니다."

## T2 temperature → categoryConsistency (repeat=5)
- temp=0.0: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.1: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.2: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.3: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.4: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.5: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.6: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.7: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.8: consistency=1.0 counts={'DELIVERY': 5}
- temp=0.9: consistency=1.0 counts={'DELIVERY': 5}
- temp=1.0: consistency=1.0 counts={'DELIVERY': 5}

## T3 repeat → categoryConsistency
- repeat=1: consistency=1.0 counts={'DELIVERY': 1}
- repeat=2: consistency=1.0 counts={'DELIVERY': 2}
- repeat=3: consistency=1.0 counts={'DELIVERY': 3}
- repeat=4: consistency=1.0 counts={'DELIVERY': 4}
- repeat=5: consistency=1.0 counts={'DELIVERY': 5}
- repeat=6: consistency=1.0 counts={'DELIVERY': 6}
- repeat=7: consistency=1.0 counts={'DELIVERY': 7}
- repeat=8: consistency=1.0 counts={'DELIVERY': 8}
- repeat=9: consistency=1.0 counts={'DELIVERY': 9}
- repeat=10: consistency=1.0 counts={'DELIVERY': 10}

## T4 강건성 (의도=배달위치 → category DELIVERY 기대, repeat=5)
- **D1-경상** "내 주문 오데꼬 있노? 언제 오노?": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **D2-전라** "주문한 거 어디쯤 왔당가? 언제 온당가?": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **D3-제주** "주문헌 거 어디 시니? 언제 오느니?": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **F1-EN** "Where is my order 1234": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **F2-JA** "注文した1234はどこですか？": consistency=0.8 counts={'ORDER': 4, 'DELIVERY': 1} 존댓말=5/5
- **F3-ZH** "我的订单1234到哪了？": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **F4-VI** "Đơn hàng 1234 của tôi ": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **G1-슬랭** "내 주문 어디감 ㅋㅋ 개꿀탱이었는데 왜 ": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **G2-슬랭** "주문 쌰갈... 배달 언제 오노 진짜": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
- **G3-슬랭** "주문 어디? 점메추 말고 내 음식 추적좀": consistency=1.0 counts={'DELIVERY': 5} 존댓말=5/5
