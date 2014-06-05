(library (fuse)
  (export
    gf-start)
  (import
    (sph)
    (rnrs base)
    (only (guile) define* load-extension cons*))

  (load-extension "libguile-fuse" "init_guile_fuse")

  (define* (gf-start mount-path #:optional (arguments (list))) "string
    symbol-alist -> fuse is started without multithreaded operation,
    with a fixed -s argument, because of the unresolved problem that
    calling guile procedures in that case leads to a segmentation
    fault"
    (primitive-gf-start (cons* "-" mount-path "-s" arguments))))