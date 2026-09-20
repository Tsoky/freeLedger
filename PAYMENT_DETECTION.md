# 支付检测设计说明

## 为什么使用通知监听

Android 的 `NotificationListenerService` 可以在用户主动授权“通知使用权”之后，收到新通知事件。这个 MVP 使用该系统能力来发现包含金额的支付通知。

监听服务：
- `PaymentNotificationListener.java`

解析器：
- `PaymentParser.java`

提醒：
- `DetectionNotifier.java`

快速确认页面：
- `QuickEntryActivity.java`

## 为什么不自动直接记账

通知文本可能出现：
- 余额；
- 优惠前金额；
- 退款金额；
- 银行营销通知；
- 重复推送。

所以 MVP 默认：
1. 检测；
2. 预填；
3. 用户确认；
4. 再写账。

这比“检测到就自动写入”更安全。

## 解析策略

当前依次尝试：
1. `支付/消费/扣款 ... ￥38.50`
2. `￥38.50 ... 支付/消费/扣款`
3. 独立货币符号金额
4. `38.50 元`

并要求通知里命中至少一个用户配置的支付关键词。

## 去重

同一检测 signature 在 90 秒内不会重复弹出。

## 商家记忆

用户确认一笔支出后，会保存：
- merchant -> categoryId
- merchant -> accountId

下次相同商家会自动预选上次的分类和账户。

如果该分类在本月恰好只有一个未用完的关联计划，也会自动建议该计划。
