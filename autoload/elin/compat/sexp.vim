function! elin#compat#sexp#get_top_list(lnum, col) abort
  return s:get_top_list(a:lnum, a:col)
endfunction

function! elin#compat#sexp#get_list(lnum, col) abort
  return s:get_list(a:lnum, a:col)
endfunction

function! elin#compat#sexp#get_expr(lnum, col) abort
  return s:get_expr(a:lnul, a:col)
endfunction

if has('nvim')
  function! s:get_top_list(lnum, col) abort
    return luaeval('require("vim-elin.sexp").get_top_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction

  function! s:get_list(lnum, col) abort
    return luaeval('require("vim-elin.sexp").get_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction

  function! s:get_expr(lnum, col) abort
    return luaeval('require("vim-elin.sexp").get_expr(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction
endif
