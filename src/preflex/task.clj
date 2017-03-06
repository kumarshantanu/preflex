;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns preflex.task
  (:require
    [preflex.internal :as i])
  (:import
    [preflex.instrument      EventHandler EventHandlerFactory]
    [preflex.instrument.task InstrumentingWrapper Wrapper]))


(defn make-wrapper
  "Given an instrumenting caller f (arity-2 fn that accepts context and no-arg fn as args), return a Wrapper instance."
  [f]
  (reify Wrapper
    (run  [_ context task]         (f context #(.run  task)))
    (run  [_ context task a]       (f context #(.run  task)))
    (run  [_ context task a b]     (f context #(.run  task)))
    (run  [_ context task a b c]   (f context #(.run  task)))
    (run  [_ context task a b c d] (f context #(.run  task)))
    (call [_ context task]         (f context #(.call task)))
    (call [_ context task a]       (f context #(.call task)))
    (call [_ context task a b]     (f context #(.call task)))
    (call [_ context task a b c]   (f context #(.call task)))
    (call [_ context task a b c d] (f context #(.call task)))))


(defn make-event-handler
  [{:keys [before
           on-return
           on-throw
           after]
    :or {before    i/nop
         on-return i/nop
         on-throw  i/nop
         after     i/nop}}]
  (reify EventHandler
    (before   [_]        (before))
    (onReturn [_]        (on-return))
    (onReturn [_ result] (on-return result))
    (onThrow  [_ thrown] (on-throw thrown))
    (after    [_]        (after))))


(defn make-event-handler-factory
  "Given factory fn (accepts event as argument, returns EventHandler), create and return an EventHandlerFactory object."
  [f]
  (reify EventHandlerFactory
    (createHandler [_ event] (f event))))


(defmacro wrap-proxy
  "Given an instrumenting caller f (that accepts context and no-arg fn as args), a target object and proxy
  class/interfaces and constructor args, return an instrumented proxy."
  [f object class-and-interfaces args {:keys [method-pred]
                                       :or {method-pred i/public-method?}
                                       :as options}]
  (i/expected vector? "class-and-interfaces to be a vector" class-and-interfaces)
  (i/expected #(every? symbol? %) "every element in class-and-interfaces to be a symbol" class-and-interfaces)
  (let [specs (i/type-methods method-pred class-and-interfaces)
        ins-f (gensym "instrumentor-")
        t-obj (gensym "target-object-")
        exprs (->> specs
                (map (fn [spec-batch]
                       (let [{:keys [declaring-class name]} (first spec-batch)
                             instru-label (str declaring-class "/" name)]
                         (->> spec-batch
                           (map (fn [each-spec]
                                  (let [{:keys [arity]} each-spec
                                        method-args  (-> arity
                                                       (repeatedly gensym)
                                                       vec)]
                                    `(~method-args
                                       (~ins-f
                                         ~instru-label #(~(symbol (str "." name)) ~t-obj ~@method-args))))))
                           (concat `(~name)))))))]
    `(let [~t-obj ~object
           ~ins-f ~f]
       (proxy ~class-and-interfaces ~args
         ~@exprs))))
