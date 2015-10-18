;short type names. requires >= C99 standard
(define-macro
  pointer uintptr-t
  b0 void
  b8 uint8_t
  b16 uint16_t
  b32 uint32_t
  b64 uint64_t
  b8_s int8_t
  b16_s int16_t
  b32_s int32_t
  b64_s int64_t)

(define-macro null (convert-type 0 b0))
(define-macro _readonly const)
(define-macro _noalias restrict)
(define-macro (increment-one arg) (set arg (+ 1 arg)))
(define-macro (decrement-one arg) (set arg (- arg 1)))

(define-macro (debug-log format ...)
  (fprintf stderr (pre-string-concat "%s:%d " format "\\n") __func__ __LINE__ __VA_ARGS__))

(define-macro scm-first SCM-CAR)
(define-macro scm-tail SCM-CDR)
