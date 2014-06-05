(define-macro file-handles-max 400)
(define file-handles SCM* file-handles-pos SCM*)
(define file-handle-next-mutex pthread-mutex-t)

(define-macro (file-handles-init) (set file-handles (malloc (* file-handles-max (sizeof pointer))))
  (set file-handles-pos file-handles))

(define-macro (file-handles-deinit) (free file-handles) (set file-handles-pos 0))

(define-macro (file-handles-pos-index)
  (/ (- (convert-type file-handles-pos b64) (convert-type file-handles b64)) (sizeof SCM*)))

(define (file-handle-next) SCM*
  (pthread-mutex-lock (address-of file-handle-next-mutex)) (define count b32 file-handles-max)
  (while (and (deref file-handles-pos) count)
    (if (< (file-handles-pos-index) file-handles-max) (increment-one file-handles-pos)
      (set file-handles-pos file-handles))
    (decrement-one count))
  (define result SCM*)
  (if count (set result file-handles-pos)
    (begin (debug-log "%s" "maximum number of open files has been reached") (set result 0)))
  (pthread-mutex-unlock (address-of file-handle-next-mutex)) (return result))

(define-macro (file-handle-set handle file-info)
  ;get a file handle from the list of open file handles
  (set handle
    (if* (struct-ref (deref file-info) fh)
      (deref (convert-type (struct-ref (deref file-info) fh) SCM*)) SCM-BOOL-F)))

(define-macro file-handle-init (define file-handle SCM) (file-handle-set file-handle file-info))

(define-macro (file-handle-add-if file-info handle)
  ;add a file-handle to the list of open file handles if its value is not a boolean.
  (if (scm-is-false (scm-boolean? handle))
    (begin (define file-handle SCM* (file-handle-next))
      (if file-handle
        (begin (set (deref file-handle) handle)
          (struct-set (deref file-info) fh (convert-type file-handle uint64_t)))))))

(define-macro (file-handle-remove-if file-info file-handle)
  ;remove a file handle from the list of open file handles
  (if (struct-ref (deref file-info) fh)
    (begin (set (deref (convert-type (struct-ref (deref file-info) fh) SCM*)) 0)
      (struct-set (deref file-info) fh 0))))