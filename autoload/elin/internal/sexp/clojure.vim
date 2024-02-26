function! s:search_ns_form_pos() abort
  let line = getline(1)
  if line ==# '(ns' || line[0:3] ==# '(ns '
    return [1, 1]
  elseif line ==# '(in-ns' || line[0:7] ==# '(in-ns '
    return [1, 1]
  else
    let p = getcurpos()
    try
      let [l1, c1] = searchpos('(ns[ \r\n]', 'n')
      let [l2, c2] = searchpos('(in-ns[ \r\n]', 'n')
      if l1 == 0 && l2 == 0
        return [-1, -1]
      elseif l1 != 0 && l2 == 0
        return elin#internal#sexp#get_list(l1, c1)['code']
        return [l1, c1]
      elseif l1 == 0 && l2 != 0
        return [l2, c2]
      elseif l1 < l2
        return [l1, c1]
      else
        return [l2, c2]
      endif
    finally
      call setpos('.', p)
    endtry
  endif
endfunction

function! elin#internal#sexp#clojure#get_ns_form() abort
  let [lnum, col] = s:search_ns_form_pos()
  if lnum == -1 && col == -1
    return ''
  endif
  return elin#internal#sexp#get_list(lnum, col)['code']
endfunction
