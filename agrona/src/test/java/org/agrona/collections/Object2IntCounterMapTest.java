/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.agrona.collections;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class Object2IntCounterMapTest
{
    private static final int INITIAL_VALUE = 0;

    private final Object2IntCounterMap<Integer> map = new Object2IntCounterMap<>(INITIAL_VALUE);

    @Test
    void shouldInitiallyBeEmpty()
    {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void getShouldReturnInitialValueWhenEmpty()
    {
        assertEquals(INITIAL_VALUE, map.get(1));
    }

    @Test
    void getShouldReturnInitialValueWhenThereIsNoElement()
    {
        map.put(1, 1);

        assertEquals(INITIAL_VALUE, map.get(2));
    }

    @Test
    void getShouldReturnPutValues()
    {
        map.put(1, 1);

        assertEquals(1, map.get(1));
    }

    @Test
    void putShouldReturnOldValue()
    {
        map.put(1, 1);

        assertEquals(1, map.put(1, 2));
    }

    @Test
    void clearShouldResetSize()
    {
        map.put(1, 1);
        map.put(100, 100);

        map.clear();

        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void clearShouldRemoveValues()
    {
        map.put(1, 1);
        map.put(100, 100);

        map.clear();

        assertEquals(INITIAL_VALUE, map.get(1));
        assertEquals(INITIAL_VALUE, map.get(100));
    }

    @SuppressWarnings("unchecked")
    @Test
    void forEachShouldLoopOverEveryElement()
    {
        map.put(1, 1);
        map.put(100, 100);

        final ObjIntConsumer<Integer> mockConsumer = mock(ObjIntConsumer.class);
        map.forEach(mockConsumer);

        final InOrder inOrder = inOrder(mockConsumer);
        inOrder.verify(mockConsumer).accept(100, 100);
        inOrder.verify(mockConsumer).accept(1, 1);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldNotContainKeyOfAMissingKey()
    {
        assertFalse(map.containsKey(1));
    }

    @Test
    void shouldContainKeyOfAPresentKey()
    {
        map.put(1, 1);

        assertTrue(map.containsKey(1));
    }

    @Test
    void shouldNotContainValueForAMissingEntry()
    {
        assertFalse(map.containsValue(1));
    }

    @Test
    void shouldContainValueForAPresentEntry()
    {
        map.put(1, 1);

        assertTrue(map.containsValue(1));
    }

    @Test
    void removeShouldReturnMissing()
    {
        assertEquals(INITIAL_VALUE, map.remove(1));
    }

    @Test
    void removeShouldReturnValueRemoved()
    {
        map.put(1, 2);

        assertEquals(2, map.remove(1));
    }

    @Test
    void removeShouldRemoveEntry()
    {
        map.put(1, 2);

        map.remove(1);

        assertTrue(map.isEmpty());
        assertFalse(map.containsKey(1));
        assertFalse(map.containsValue(2));
    }

    @Test
    void shouldOnlyRemoveTheSpecifiedEntry()
    {
        IntStream.range(1, 8).forEach((i) -> map.put(i, i * 2));

        map.remove(5);

        IntStream
            .range(1, 8)
            .filter((i) -> i != 5L)
            .forEach((i) ->
            {
                assertTrue(map.containsKey(i));
                assertTrue(map.containsValue(2 * i));
            });
    }

    @Test
    void shouldResizeWhenMoreElementsAreAdded()
    {
        IntStream
            .range(1, 100)
            .forEach((key) ->
            {
                final int value = key * 2;
                assertEquals(INITIAL_VALUE, map.put(key, value));
                assertEquals(value, map.get(key));
            });
    }

    @Test
    void shouldHaveNoMinValueForEmptyCollection()
    {
        assertEquals(INITIAL_VALUE, map.minValue());
    }

    @Test
    void shouldFindMinValue()
    {
        map.put(1, 2);
        map.put(2, 10);
        map.put(3, -5);

        assertEquals(-5, map.minValue());
    }

    @Test
    void shouldHaveNoMaxValueForEmptyCollection()
    {
        assertEquals(INITIAL_VALUE, map.maxValue());
    }

    @Test
    void shouldFindMaxValue()
    {
        map.put(1, 2);
        map.put(2, 10);
        map.put(3, -5);

        assertEquals(10, map.maxValue());
    }

    @Test
    void sizeShouldReturnNumberOfEntries()
    {
        final int count = 100;
        for (int key = 0; key < count; key++)
        {
            map.put(key, 1);
        }

        assertEquals(count, map.size());
    }

    @Test
    void shouldNotSupportLoadFactorOfGreaterThanOne()
    {
        assertThrows(IllegalArgumentException.class, () -> new Object2IntCounterMap<>(4, 2, 0));
    }

    @Test
    void shouldNotSupportLoadFactorOfOne()
    {
        assertThrows(IllegalArgumentException.class, () -> new Object2IntCounterMap<>(4, 1, 0));
    }

    @Test
    void shouldComputeIfAbsent()
    {
        final int testKey = 7;
        final int testValue = 7;

        final ToIntFunction<Integer> function = (i) -> testValue;

        assertEquals(map.initialValue(), map.get(testKey));

        assertThat(map.computeIfAbsent(testKey, function), is(testValue));
        assertThat(map.get(testKey), is(testValue));
    }

    @Test
    void shouldContainValueForIncAndDecEntries()
    {
        map.incrementAndGet(1);
        map.getAndIncrement(2);
        map.getAndAdd(3, 2);
        map.addAndGet(4, 3);
        map.decrementAndGet(5);
        map.getAndDecrement(6);

        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertTrue(map.containsKey(3));
        assertTrue(map.containsKey(4));
        assertTrue(map.containsKey(5));
        assertTrue(map.containsKey(6));
    }

    @Test
    void shouldResultInEmptyAfterIncAndDecWhenEmpty()
    {
        map.incrementAndGet(1);
        map.decrementAndGet(1);

        assertTrue(map.isEmpty());

        map.getAndIncrement(1);
        map.getAndDecrement(1);

        assertTrue(map.isEmpty());

        map.incrementAndGet(1);
        map.getAndDecrement(1);

        assertTrue(map.isEmpty());

        map.getAndIncrement(1);
        map.decrementAndGet(1);

        assertTrue(map.isEmpty());
    }

    @Test
    void shouldResultInNotEmptyAfterIncAndDecWhenHaveKey()
    {
        map.put(1, 1);

        map.incrementAndGet(1);
        map.decrementAndGet(1);

        assertEquals(1, map.get(1));
        assertEquals(1, map.size());
    }

    @Test
    void shouldReturnInitialValueForGetAndAdd0IfKeyMissing()
    {
        final int val = map.getAndAdd(1, 0);

        assertEquals(INITIAL_VALUE, val);
        assertTrue(map.isEmpty());
    }

    @Test
    void shouldReturnOldValueForGetAndAdd0IfKeyExists()
    {
        map.put(1, 1);
        final int val = map.getAndAdd(1, 0);

        assertEquals(1, val);
        assertEquals(1, map.size());
    }

    @Test
    void shouldReturnOldValueForGetAndAddNot0IfKeyExists()
    {
        map.put(1, 1);
        int amount;
        do
        {
            amount = ThreadLocalRandom.current().nextInt();
        }
        while (amount == 0 || amount == -1); // -1 would change size, checked separately
        final int val = map.getAndAdd(1, amount);

        assertEquals(1, val);
        assertEquals(1, map.size());
    }

    @Test
    void shouldRemoveEntryAfterDecToInitialVal()
    {
        map.put(1, INITIAL_VALUE + 1);

        map.decrementAndGet(1);

        assertEquals(INITIAL_VALUE, map.get(1));
        assertTrue(map.isEmpty());
        assertFalse(map.containsKey(1));
    }

    @Test
    void shouldRemoveEntryAfterIncToInitialVal()
    {
        map.put(1, INITIAL_VALUE - 1);

        map.incrementAndGet(1);

        assertEquals(INITIAL_VALUE, map.get(1));
        assertTrue(map.isEmpty());
        assertFalse(map.containsKey(1));
    }

    @Test
    void shouldNotAllowInitialValueAsValue()
    {
        assertThrows(IllegalArgumentException.class, () -> map.put(1, INITIAL_VALUE));
    }

    @Test
    void shouldNotContainInitialValue()
    {
        assertFalse(map.containsValue(INITIAL_VALUE));
        map.put(INITIAL_VALUE, 1);
        assertFalse(map.containsValue(INITIAL_VALUE));
    }

    @Test
    void shouldCompactMap()
    {
        final Object2IntCounterMap<Integer> map = new Object2IntCounterMap<>(2, 0.9f, Integer.MIN_VALUE);
        assertEquals(8, map.capacity());
        assertEquals(7, map.resizeThreshold());
        assertEquals(0, map.size());

        for (int i = 1; i <= 16; i++)
        {
            map.put(i, i * -123);
        }
        assertEquals(32, map.capacity());
        assertEquals(28, map.resizeThreshold());
        assertEquals(16, map.size());

        for (int i = 6; i <= 16; i++)
        {
            assertEquals(-123 * i, map.remove(i));
        }
        assertEquals(32, map.capacity());
        assertEquals(28, map.resizeThreshold());
        assertEquals(5, map.size());

        map.compact();
        assertEquals(8, map.capacity());
        assertEquals(7, map.resizeThreshold());
        assertEquals(5, map.size());
    }

    @Test
    void shouldCompareKeyCorrectly()
    {
        final Object2IntCounterMap<CharSequenceKey> map = new Object2IntCounterMap<>(0);
        map.put(new CharSequenceKey("abc"), 100);

        final CharSequenceKey xyzKey = new CharSequenceKey("xyz");
        map.put(xyzKey, 2);

        assertEquals(100, map.get(new CharSequenceKey("abc")));
        assertEquals(101, map.incrementAndGet(new CharSequenceKey("abc")));
        assertEquals(100, map.decrementAndGet(new CharSequenceKey("abc")));
        assertTrue(map.containsKey(new CharSequenceKey("abc")));

        assertEquals(2, map.get(xyzKey));
        assertEquals(17, map.addAndGet(xyzKey, 15));
        assertEquals(3, map.addAndGet(new CharSequenceKey("xyz"), -14));
        assertTrue(map.containsKey(xyzKey));
    }
}
