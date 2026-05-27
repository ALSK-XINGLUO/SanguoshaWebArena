# 已知问题

## 1. 铁索连环传导濒死中断

当铁索连环传导伤害导致一方濒死时，`propagateChainDamage` 通过 `return` 提前退出，剩余横置目标的传导被跳过。

- **影响范围：** 多人场景（（当前仅 1v1 影响较小，但若有连环涉及第三方则传导不完整）
- **优先级：** 中
- **后续：** 多人模式时需改为 continuation 队列，与 AOE/濒死统一

## 2. 闪电传导濒死后遗漏原目标濒死检查

`applyDelayTrickEffect` 中闪电判定流程：先 `takeDamage(3)`，再 `propagateChainDamage`，最后 `checkDying`。
若连锁传导引发濒死，`return` 后闪电原目标的 `checkDying` 被跳过。

- **影响范围：** 闪电伤害 + 铁索连环双重场景
- **优先级：** 中
- **当前表现：** 闪电原目标虽然在 `takeDamage` 中已 `setAlive(false)`，但不会进入濒死求援流程，略显突兀
- **后续：** 结算栈化后可按顺序依次处理所有伤害的濒死

## 3. 杀命中濒死中断伤害后触发

`handleRespondShan` 中 `checkDying` 返回后直接 return，其后的 `checkGameOver`、`fireEvent(DAMAGE_DONE)`、装备触发（（麒麟弓、贯石斧、寒冰剑、青龙偃月刀等）均被跳过。

- **影响范围：** 所有依赖 DAMAGE_DONE 事件的伤害后触发类装备/技能
- **优先级：** 中高
- **后续：** 建议建立统一结算栈，将伤害、濒死、触发事件排队处理，避免 return 中断后续流程

---

*记录于 2026-05-27，濒死救援队列重写后代码审计。*
