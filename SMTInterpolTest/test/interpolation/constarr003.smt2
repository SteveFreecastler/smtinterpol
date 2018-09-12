(set-option :produce-proofs true)
(set-option :proof-check-mode true)
(set-option :interpolant-check-mode true)
(set-option :print-terms-cse false)

(set-logic QF_AX)
(declare-sort U 0)
(declare-fun v () U)
(declare-fun w1 () U)
(declare-fun w2 () U)
(declare-fun i () U)
(declare-fun k1 () U)
(declare-fun k2 () U)
(declare-fun a () (Array U U))
(declare-fun s () (Array U U))

(assert (! (and (not (= (select a i) v)) (= s (store a k1 w1))
(not (= i k1)) (not (= i k2))) :named A))
(assert (! (= s (store ((as const (Array U U)) v) k2 w2)) :named B))

(check-sat)
(get-proof)
(get-interpolants A B)
(exit)
