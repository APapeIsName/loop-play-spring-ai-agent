# Quest 2 — SYSTEM_PROMPT 수정 전/후 비교

시나리오: "라이더가 음식을 엎었다는데 보상 받을 수 있나요?", repeat=5

## BASELINE(B안)
- categoryConsistency = **1.0**
- categoryCounts = {'DELIVERY': 5}
- urgencyCounts = {'NORMAL': 5}
- 샘플 replyToCustomer:
  1. [DELIVERY/NORMAL] 죄송합니다. 음식이 엎어진 사태에 대해 감사드립니다. 주문번호를 알려주시면 확인 후 조치하겠습니다.
  2. [DELIVERY/NORMAL] 죄송합니다. 음식이 엎어진 상황에 대해 감정을 이해하겠습니다. 주문번호를 알려주시면 확인 후 답변 드리겠습니다.
  3. [DELIVERY/NORMAL] 죄송합니다. 라이더가 음식을 엎었다는 말씀 감사드립니다. 확인해보겠습니다. 주문 번호를 알려주시겠어요?
  4. [DELIVERY/NORMAL] 죄송합니다. 음식이 엎어진 것 같아요. 주문번호를 알려주시면 확인해보겠습니다.
  5. [DELIVERY/NORMAL] 죄송합니다. 음식을 엎은 사태에 대해 감사드립니다. 주문번호를 알려주시면 확인 후 답변 드리겠습니다.

## MODIFIED(수정안)
- categoryConsistency = **1.0**
- categoryCounts = {'REFUND': 5}
- urgencyCounts = {'NORMAL': 5}
- 샘플 replyToCustomer:
  1. [REFUND/NORMAL] 죄송합니다. 음식이 엎어졌다는 말씀 감사드립니다. 확인을 위해 주문번호를 알려주시겠어요?
  2. [REFUND/NORMAL] 죄송합니다. 음식이 엎어진 사태에 대해 감사드립니다. 확인해보겠습니다.
  3. [REFUND/NORMAL] 죄송합니다. 음식을 엎은 사태에 대해 감사드립니다. 확인해보겠습니다.
  4. [REFUND/NORMAL] 죄송합니다. 음식이 엎어진 사태에 대해 감사드립니다. 확인을 위해 주문번호를 알려주시겠어요?
  5. [REFUND/NORMAL] 죄송합니다. 음식을 엎어진 사태에 대해 감정이입했습니다. 주문번호를 알려주시면 확인 후 보상 가능성을 말씀드리겠습니다.
