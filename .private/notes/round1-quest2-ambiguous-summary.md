# Quest 2 — 모호 시나리오 재실험

시나리오: "방금 시킨 주문 취소하고 싶어요. 환불은 얼마나 걸려요?" (ORDER↔REFUND 경계), repeat=10

## R1 단순 vs 구조화 (어려운 입력에서 구조화가 일관성↑?)
- **simple**: consistency=1.0 counts={'REFUND': 10}
- **struct**: consistency=1.0 counts={'REFUND': 10}

## R2 temperature 영향 (구조화, 어려운 입력)
- temp=0.0: consistency=1.0 counts={'REFUND': 10}
- temp=0.3: consistency=1.0 counts={'REFUND': 10}
- temp=0.7: consistency=1.0 counts={'REFUND': 10}
- temp=1.0: consistency=1.0 counts={'REFUND': 10}
