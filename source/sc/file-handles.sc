(pre-define file-handles-max 400)
(declare file-handles SCM)
(sc-comment "index 0 is reserved for errors")
(define file-handles-index b64 1)
(declare file-handle-next-mutex pthread-mutex-t)

(pre-define (file-handles-init)
  (begin
    (scm_permanent_object file-handles)
    (set file-handles (scm-c-make-vector (+ 1 file-handles-max) SCM-BOOL-F))))

(define (file-handle-next) b64
  (pthread-mutex-lock (address-of file-handle-next-mutex))
  (define tries-left b32 file-handles-max)
  (while (and (scm-is-true (SCM-SIMPLE-VECTOR-REF file-handles file-handles-index)) tries-left)
    (set file-handles-index
      (if* (< file-handles-index file-handles-max) (+ 1 file-handles-index)
        1))
    (decrement-one tries-left))
  (define result b64
    (if* tries-left file-handles-index
      (begin
        (debug-log "%s" "too many concurrently open file-handles")
        0)))
  (pthread-mutex-unlock (address-of file-handle-next-mutex))
  (return result))

(pre-define (file-handle-set file-info handle)
  (begin
    "get a file handle from the list of open file handles"
    (set handle
      (if* file-info:fh (SCM-SIMPLE-VECTOR-REF file-handles file-info:fh)
        SCM-BOOL-F))))

(pre-define file-handle-init
  (begin
    (declare file-handle SCM)
    (file-handle-set file-info file-handle)))

(pre-define (file-handle-add-if file-info handle)
  (begin
    "add a file-handle to the list of open file handles if its value is not a boolean"
    (if (scm-is-false (scm-boolean? handle))
      (begin
        (define index b64 (file-handle-next))
        (if index
          (begin
            (SCM-SIMPLE-VECTOR-SET file-handles index handle)
            (set file-info:fh index)))))))

(pre-define (file-handle-remove file-info file-handle)
  (if file-info:fh
    (begin
      (SCM-SIMPLE-VECTOR-SET file-handles file-info:fh SCM-BOOL-F)
      (set file-info:fh 0))))