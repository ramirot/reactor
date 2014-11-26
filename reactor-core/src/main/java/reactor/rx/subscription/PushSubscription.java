/*
 * Copyright (c) 2011-2014 Pivotal Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package reactor.rx.subscription;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.function.Consumer;
import reactor.rx.Stream;
import reactor.rx.action.support.SpecificationExceptions;
import reactor.rx.subscription.support.WrappedSubscription;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Relationship between a Stream (Publisher) and a Subscriber. A PushSubscription offers common facilities to track
 * downstream demand. Subclasses such as ReactiveSubscription implement these mechanisms to prevent Subscriber overrun.
 * <p>
 * In Reactor, a subscriber can be an Action which is both a Stream (Publisher) and a Subscriber.
 *
 * @author Stephane Maldini
 */
public class PushSubscription<O> implements Subscription, Consumer<Long> {
	protected final Subscriber<? super O> subscriber;
	protected final Stream<O>             publisher;

	protected volatile int terminated = 0;

	protected static final AtomicIntegerFieldUpdater<PushSubscription> TERMINAL_UPDATED = AtomicIntegerFieldUpdater
			.newUpdater(PushSubscription.class, "terminated");


	protected long pendingRequestSignals = 0l;

	/**
	 * Wrap the subscription behind a push subscription to start tracking its requests
	 *
	 * @param subscription the subscription to wrap
	 * @return the new ReactiveSubscription
	 */
	public static <O> PushSubscription<O> wrap(Subscription subscription, Subscriber<? super O> errorSubscriber) {
		return new WrappedSubscription<O>(subscription, errorSubscriber);
	}

	public PushSubscription(Stream<O> publisher, Subscriber<? super O> subscriber) {
		this.subscriber = subscriber;
		this.publisher = publisher;
	}

	@Override
	public void accept(Long n) {
		request(n);
	}

	@Override
	public void request(long n) {
		try {
			if (publisher == null) {
				if (pendingRequestSignals != Long.MAX_VALUE && (pendingRequestSignals += n) < 0)
					subscriber.onError(SpecificationExceptions.spec_3_17_exception(subscriber, pendingRequestSignals, n));
			}
			onRequest(n);
		} catch (Throwable t) {
			subscriber.onError(t);
		}

	}

	@Override
	public void cancel() {
		TERMINAL_UPDATED.set(this, 1);
		if(publisher != null){
			publisher.cleanSubscriptionReference(this);
		}
	}

	public void onComplete() {
		if (TERMINAL_UPDATED.compareAndSet(this, 0, 1) && subscriber != null) {
				subscriber.onComplete();
		}
	}

	public void onNext(O ev) {
		if(terminated == 0) {
			subscriber.onNext(ev);
		}
	}

	public void onError(Throwable throwable) {
		if (subscriber != null) {
			subscriber.onError(throwable);
		}
	}

	public Stream<O> getPublisher() {
		return publisher;
	}

	public boolean hasPublisher() {
		return publisher != null;
	}

	public void updatePendingRequests(long n) {
		if ((pendingRequestSignals += n) < 0) pendingRequestSignals = Long.MAX_VALUE;
	}

	public long clearPendingRequest() {
		return pendingRequestSignals;
	}

	protected void onRequest(long n) {
		//IGNORE, full push
	}

	public final Subscriber<? super O> getSubscriber() {
		return subscriber;
	}

	public boolean isComplete() {
		return TERMINAL_UPDATED.get(this) == 1;
	}

	public final long pendingRequestSignals() {
		return pendingRequestSignals;
	}

	public void incrementCurrentNextSignals() {
		/*
		Count operation for each data signal
		 */
	}

	public void maxCapacity(long n) {
		/*
		Adjust capacity (usually the number of elements to be requested at most)
		 */
	}

	public boolean shouldRequestPendingSignals() {
		/*
		Should request the next batch of pending signals. Usually when current next signals reaches some limit like the
		maxCapacity.
		 */
		return false;
	}

	@Override
	public int hashCode() {
		int result = subscriber.hashCode();
		result = 31 * result + publisher.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PushSubscription that = (PushSubscription) o;

		if (publisher.hashCode() != that.publisher.hashCode()) return false;
		if (!subscriber.equals(that.subscriber)) return false;

		return true;
	}

	@Override
	public String toString() {
		return "{push}";
	}


}