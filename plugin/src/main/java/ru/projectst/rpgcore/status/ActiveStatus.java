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

    /** Когда статус лёг впервые: по этому ряды значков держат порядок. */
    private final long appliedAtTick;

    /**
     * С какого тика идёт нынешний срок. Обновление и новый стак начинают срок
     * заново, продление — нет: продлённый статус тянет прежний срок дальше.
     */
    private long renewedAtTick;

    ActiveStatus(StatusDef def, String source, int stacks, long now, long expiresAtTick,
                 double amount) {
        this.def = def;
        this.source = source;
        this.stacks = stacks;
        this.appliedAtTick = now;
        this.renewedAtTick = now;
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

    public long appliedAtTick() {
        return appliedAtTick;
    }

    /**
     * Полный нынешний срок в тиках: от начала до конца.
     *
     * <p>Нужен показу убывания: «осталось 3 из 10». Брать длительность из
     * объявления статуса нельзя — навык накладывает его на свою длительность,
     * а продление растягивает срок.
     */
    public long total() {
        return Math.max(1, expiresAtTick - renewedAtTick);
    }

    public boolean expired(long now) {
        return now >= expiresAtTick;
    }

    void refresh(long now, long newExpiry) {
        this.renewedAtTick = now;
        this.expiresAtTick = newExpiry;
    }

    void extend(int ticks) {
        this.expiresAtTick += ticks;
    }

    void addStack(long now, long newExpiry) {
        this.stacks = Math.min(def.maxStacks(), stacks + 1);
        this.renewedAtTick = now;
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
