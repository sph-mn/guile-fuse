(library (fuse)
  (export
    gf-start)
  (import
    (rnrs base)
    (sph)
    (only (guile) define* load-extension current-module))

  (load-extension "libguile-fuse" "init_guile_fuse")

  (define* (gf-start mount-path #:optional (arguments (list)) (env (current-module)))
    "string
    symbol-alist -> fuse is started without multithreaded operation,
    with a fixed -s argument, because of the unresolved problem that
    calling guile procedures in that case leads to a segmentation
    fault"
    (primitive-gf-start (pairs "-" mount-path "-s" arguments) env)))