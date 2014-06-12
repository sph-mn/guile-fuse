(define-macro file-handles-max 400)
(define file-handles SCM)
;index 0 is reserved for errors
(define file-handles-index b64 1)
(define file-handle-next-mutex pthread-mutex-t)

(define-macro (file-handles-init) (scm_permanent_object file-handles)
  (set file-handles (scm-c-make-vector (+ 1 file-handles-max) SCM-BOOL-F)))

(define (file-handle-next) b64
  (pthread-mutex-lock (address-of file-handle-next-mutex)) (define tries-left b32 file-handles-max)
  (while (and (scm-is-true (SCM-SIMPLE-VECTOR-REF file-handles file-handles-index)) tries-left)
    (set file-handles-index
      (if* (< file-handles-index file-handles-max) (+ 1 file-handles-index) 1))
    (decrement-one tries-left))
  (define result b64
    (if* tries-left file-handles-index
      (begin (debug-log "%s" "too many concurrently open file-handles") 0)))
  (pthread-mutex-unlock (address-of file-handle-next-mutex)) (return result))

(define-macro (file-handle-set file-info handle)
  ;get a file handle from the list of open file handles
  (set handle
    (if* (struct-ref (deref file-info) fh)
      (SCM-SIMPLE-VECTOR-REF file-handles (struct-ref (deref file-info) fh)) SCM-BOOL-F)))

(define-macro file-handle-init (define file-handle SCM) (file-handle-set file-info file-handle))

(define-macro (file-handle-add-if file-info handle)
  ;add a file-handle to the list of open file handles if its value is not a boolean
  (if (scm-is-false (scm-boolean? handle))
    (begin (define index b64 (file-handle-next))
      (if index
        (begin (SCM-SIMPLE-VECTOR-SET file-handles index handle)
          (struct-set (deref file-info) fh index))))))

(define-macro (file-handle-remove file-info file-handle)
  (if (struct-ref (deref file-info) fh)
    (begin (SCM-SIMPLE-VECTOR-SET file-handles (struct-ref (deref file-info) fh) SCM-BOOL-F)
      (struct-set (deref file-info) fh 0))))