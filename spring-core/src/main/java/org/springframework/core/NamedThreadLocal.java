/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

import org.springframework.util.Assert;

import java.util.function.Supplier;

/**
 * {@link ThreadLocal} subclass that exposes a specified name
 * as {@link #toString()} result (allowing for introspection).
 * ThreadLocal的子类，以 toString() 的结果暴露了一个指定的名字（以允许内省检查）。
 *
 * @param <T> the value type
 * @author Juergen Hoeller
 * @author Qimiao Chen
 * @see NamedInheritableThreadLocal
 * @since 2.5.2
 */
public class NamedThreadLocal<T> extends ThreadLocal<T> {

	private final String name;


	/**
	 * Create a new NamedThreadLocal with the given name.
	 * 构造器。
	 *
	 * @param name a descriptive name for this ThreadLocal
	 */
	public NamedThreadLocal(String name) {
		Assert.hasText(name, "Name must not be empty");
		this.name = name;
	}

	@Override
	public String toString() {
		return this.name;
	}


	/**
	 * Create a named thread local variable. The initial value of the variable is
	 * determined by invoking the {@code get} method on the {@code Supplier}.
	 * 创建一个线程本地变量，初始值通过入参 Supplier 确定。
	 *
	 * @param <S>      the type of the named thread local's value
	 * @param name     a descriptive name for the thread local
	 * @param supplier the supplier to be used to determine the initial value
	 * @return a new named thread local
	 * @since 6.1
	 */
	public static <S> ThreadLocal<S> withInitial(String name, Supplier<? extends S> supplier) {
		return new SuppliedNamedThreadLocal<>(name, supplier);
	}


	/**
	 * An extension of NamedThreadLocal that obtains its initial value from
	 * the specified {@code Supplier}.
	 * NamedThreadLocal 的扩展，包含了来自 Supplier 的初始值。
	 *
	 * @param <T> the type of the named thread local's value
	 */
	private static final class SuppliedNamedThreadLocal<T> extends NamedThreadLocal<T> {

		private final Supplier<? extends T> supplier;

		SuppliedNamedThreadLocal(String name, Supplier<? extends T> supplier) {
			super(name);
			Assert.notNull(supplier, "Supplier must not be null");
			this.supplier = supplier;
		}

		@Override
		protected T initialValue() {
			return this.supplier.get();
		}
	}

}
