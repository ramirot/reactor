/*
 * Copyright (c) 2011-2013 GoPivotal, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.rx.action;

import org.reactivestreams.spi.Subscription;
import reactor.event.dispatch.Dispatcher;
import reactor.function.Function;
import reactor.function.Predicate;

/**
 * @author Stephane Maldini
 * @since 1.1
 */
public class FilterAction<T, E extends Pipeline<T>> extends Action<T, T> {

	public static final Predicate<Boolean> simplePredicate = new Predicate<Boolean>() {
		@Override
		public boolean test(Boolean aBoolean) {
			return aBoolean;
		}
	};

	private final Predicate<T> p;
	private final E            elseComposable;

	@SuppressWarnings("unchecked")
	public FilterAction(Predicate<T> p, Dispatcher dispatcher) {
		this(p, dispatcher, (E) new Action<T,T>(dispatcher) {
			@Override
			protected void doNext(T ev) {
				broadcastNext(ev);
			}
		});
	}

	public FilterAction(final Function<T,Boolean> p, Dispatcher dispatcher) {
		this(new Predicate<T>() {
			@Override
			public boolean test(T t) {
				return p.apply(t);
			}
		}, dispatcher);
	}

	public FilterAction(Predicate<T> p, Dispatcher dispatcher, E pipeline) {
		super(dispatcher);
		this.p = p;
		this.elseComposable = pipeline;
	}

	@Override
	protected void doNext(T value) {
		if (p.test(value)) {
			broadcastNext(value);
		} else {
			elseComposable.broadcastNext(value);
			// GH-154: Verbose error level logging of every event filtered out by a Stream filter
			// Fix: ignore Predicate failures and drop values rather than notifying of errors.
			//d.accept(new IllegalArgumentException(String.format("%s failed a predicate test.", value)));
		}
	}

	@Override
	protected void doError(Throwable ev) {
		elseComposable.broadcastError(ev);
		super.doError(ev);
	}

	@Override
	protected void doComplete() {
		elseComposable.broadcastComplete();
		super.doComplete();
	}

	@Override
	protected void doFlush() {
		elseComposable.broadcastFlush();
		super.doFlush();
	}

	public E otherwise() {
		return elseComposable;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void doSubscribe(Subscription subscription) {
		super.doSubscribe(subscription);
		if(elseComposable != null && Action.class.isAssignableFrom(elseComposable.getClass())){
			((Action<T,E>)elseComposable).prefetch(batchSize).onSubscribe(subscription);
		}
	}
}