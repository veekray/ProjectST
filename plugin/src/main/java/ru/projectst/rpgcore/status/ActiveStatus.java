package ru.projectst.rpgcore.status;

/**
 * Активный статус на цели.
 *
 * <p>Изменяемый намеренно: продление и стаки правят существующий экземпляр, а
 * не создают новый, иначе пришлось бы пересобирать коллекцию на каждом
 * повторном касте. Класс внутренний для модуля, наружу уходит только чтение.
 */
public final class ActiveStatus {

    private final StatusDef def;
    private final String source;
    private int stacks;
    private long expiresAtTick;
    private double amount;

    ActiveStatus(StatusDef def, String source, int stacks, long expiresAtTick, double amount) {
        this.def = def;
        this.source = source;
        this.stacks = stacks;
        this.expiresAtTick = expiresAtTick;
        this.amount = amount;
    }

    public StatusDef def() {
        return def;
    }

    public String id() {
        return def.id();
    }

    public StatusCategory category() {
        return def.category();
    }

    public String source() {
        return source;
    }

    public int stacks() {
        return stacks;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    public double amount() {
        return amount;
    }

    public long remaining(long now) {
        return Math.max(0, expiresAtTick - now);
    }

    public boolean expired(long now) {
        return now >= expiresAtTick;
    }

    void refresh(long newExpiry) {
        this.expiresAtTick = newExpiry;
    }

    void extend(int ticks) {
        this.expiresAtTick += ticks;
    }

    void addStack(long newExpiry) {
        this.stacks = Math.min(def.maxStacks(), stacks + 1);
        this.expiresAtTick = newExpiry;
    }

    void setAmount(double value) {
        this.amount = Math.max(0, value);
    }

    @Override
    public String toString() {
        return def.id() + " x" + stacks + " до тика " + expiresAtTick
                + (amount > 0 ? " (" + amount + ")" : "") + " от " + source;
    }

    /**
     * Снимает один стак.
     *
     * @return {@code true}, если стаков больше не осталось и статус пора убрать
     */
    boolean removeStack() {
        stacks = stacks - 1;
        return stacks <= 0;
    }
}
