package ru.ruiscode.ics.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка пакетного удаления: разбиение, прогресс, частичные ошибки, повтор. */
class EventDeleterTest {

    @Test
    fun splitsIntoBatches() {
        val deleter = EventDeleter(batchSize = 50)
        val ids = (1L..120L).toList()
        val batches = mutableListOf<Int>()
        val result = deleter.delete(ids, doDelete = { batch ->
            batches.add(batch.size)
            batch.size
        })
        assertEquals(listOf(50, 50, 20), batches)
        assertEquals(120, result.deleted)
        assertTrue(result.failedIds.isEmpty())
    }

    @Test
    fun reportsProgressPerBatch() {
        val deleter = EventDeleter(batchSize = 2)
        val progress = mutableListOf<Pair<Int, Int>>()
        deleter.delete((1L..5L).toList(), doDelete = { it.size }) { done, total ->
            progress.add(done to total)
        }
        assertEquals(listOf(2 to 5, 4 to 5, 5 to 5), progress)
    }

    @Test
    fun collectsFailedBatchesAndContinues() {
        val deleter = EventDeleter(batchSize = 2)
        var call = 0
        val result = deleter.delete(
            (1L..6L).toList(),
            doDelete = { batch ->
                call++
                if (call == 2) -1 else batch.size // второй пакет «упал»
            },
        )
        assertEquals(4, result.deleted)
        assertEquals(listOf(3L, 4L), result.failedIds)
    }

    @Test
    fun exceptionInBatchIsTreatedAsFailure() {
        val deleter = EventDeleter(batchSize = 3)
        val result = deleter.delete(
            (1L..6L).toList(),
            doDelete = { batch -> if (batch.contains(4L)) throw IllegalStateException("boom") else batch.size },
        )
        assertEquals(3, result.deleted)
        assertEquals(listOf(4L, 5L, 6L), result.failedIds)
    }

    @Test
    fun retryRemovesFailedIds() {
        val deleter = EventDeleter(batchSize = 2)
        var failFirst = true
        val first = deleter.delete(
            (1L..4L).toList(),
            doDelete = { batch ->
                if (failFirst) {
                    failFirst = false
                    -1
                } else {
                    batch.size
                }
            },
        )
        assertEquals(listOf(1L, 2L), first.failedIds)
        val second = deleter.delete(first.failedIds, doDelete = { it.size })
        assertEquals(2, second.deleted)
        assertTrue(second.failedIds.isEmpty())
    }

    @Test
    fun emptyInputIsNoOp() {
        val result = EventDeleter().delete(emptyList(), doDelete = { it.size })
        assertEquals(0, result.deleted)
        assertTrue(result.failedIds.isEmpty())
    }
}
