;not used - unclear how the benefits of read-buf can be archieved with this binding

#;(define (gf-read-buf path buffers size offset file-info)
  (b32-s (const char*) (struct fuse-bufvec**) size-t off-t (struct fuse-file-info*))
  init-file-handle
  (define-scm-result
    (scm-call-4 gf-scm-read-buf (scm-from-locale-string path)
      (scm-from-size-t size) (scm-from-int offset) file-handle))
  (define bufvec (struct fuse-bufvec*) (malloc (sizeof (quote "struct fuse_bufvec"))))
  (if (not bufvec) (return -ENOMEM)) (set (deref bufvec) (FUSE-BUFVEC-INIT size))
  (let-macro (out-buf (deref (struct-ref (deref bufvec) buf)))
    (if (scm-is-bytevector scm-result)
      (begin (define size-read b32 (SCM-BYTEVECTOR-LENGTH scm-result))
        (define mem (malloc size-read)) (memcpy mem (SCM-BYTEVECTOR-CONTENTS scm-result) size-read)
        (struct-set out-buf mem mem size size-read) (return 0))
      (if (scm-is-true (scm-file-port? scm-result))
        (begin
          (struct-set out-buf flags
            (bit-or FUSE-BUF-IS-FD
              (if* (scm-is-true (scm-seek scm-result (scm-from-uint 0) (scm-from-int SEEK_CUR)))
                FUSE-BUF-FD-SEEK 0))
            fd (scm->int (scm-fileno scm-result)) pos offset)
          (return 0)))))
  (default-return -1))

;not implemented - how may this work

#;(define (gf-write-buf path buffers offset file-info)
  (b32-s (const char*) (struct fuse-bufvec*) off-t (struct fuse-file-info*)) init-file-handle
  (define-scm-result
    (scm-call-2 gf-scm-write-buf (scm-from-locale-string path) (scm-from-int offset) file-handle))
  (define out-bufvec (struct fuse-bufvec) (FUSE-BUFVEC-INIT (fuse-buf-size buffers)))
  (let-macro (out-buf (deref (struct-ref (deref bufvec) buf))))
  (fuse-buf-copy (address-of t) bufvec FUSE-BUF-SPLICE-NONBLOCK) (default-return -1))
