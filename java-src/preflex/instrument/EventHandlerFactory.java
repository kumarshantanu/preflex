/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 *   the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.instrument;

public interface EventHandlerFactory<E> {

    public static final EventHandlerFactory<?> NOP = new EventHandlerFactory<Object>() {
        @Override
        public EventHandler createHandler(Object event) {
            return EventHandler.NOP;
        }
    };

    public EventHandler createHandler(E event);

}
