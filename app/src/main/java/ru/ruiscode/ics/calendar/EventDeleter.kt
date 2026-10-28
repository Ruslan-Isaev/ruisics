package ru.ruiscode.ics.calendar

/**
 * Пакетное удаление событий. События удаляются небольшими пакетами
 * (размер настраивается внутри реализации — [batchSize]), ошибки каждого
 * пакета обрабатываются отдельно, операция не считается успешной, пока
 * не получен результат по каждому пакету. Частично неудачное удаление
 * возвращает список необработанных id — их можно передать повторной
 * попыткой.
 *
 * [delayBetweenBatchesMillis] > 0 включает постепенное (медленное)
 * удаление: между пакетами выдерживается пауза, чтобы снизить нагрузку
 * на провайдер календаря и фоновую синхронизацию.
 */
class EventDeleter(
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val delayBetweenBatchesMillis: Long = 0L,
) {

    data class Result(
        val deleted: Int,
        /** Id событий, которые не удалось удалить (ошибка пакета). */
        val failedIds: List<Long>,
    )

    /**
     * Удаляет [ids] пакетами, вызывая [doDelete] для каждого пакета
     * (возвращает число удалённых строк или -1 при ошибке).
     * [onProgress] получает (обработано, всего) после каждого пакета.
     */
    fun delete(
        ids: List<Long>,
        doDelete: (List<Long>) -> Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result {
        if (ids.isEmpty()) return Result(0, emptyList())
        var deleted = 0
        val failed = mutableListOf<Long>()
        var done = 0
        for (batch in ids.chunked(batchSize)) {
            val removed = try {
                doDelete(batch)
            } catch (e: Exception) {
                -1
            }
            if (removed >= 0) {
                deleted += removed
            } else {
                failed.addAll(batch)
            }
            done += batch.size
            onProgress(done, ids.size)
            // Пауза между пакетами в режиме постепенного удаления.
            if (delayBetweenBatchesMillis > 0 && done < ids.size) {
                Thread.sleep(delayBetweenBatchesMillis)
            }
        }
        return Result(deleted, failed)
    }

    companion object {
        /** Размер пакета выбран с учётом ограничений провайдера календаря (SQL-переменных). */
        const val DEFAULT_BATCH_SIZE = 50
    }
}
