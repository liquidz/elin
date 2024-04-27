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
  return elin#internal#sexp#get_list(lnum, col)
endfunction

function! elin#internal#sexp#clojure#replace_ns_form(new_ns) abort
  let view = winsaveview()
  let new_ns = trim(a:new_ns)
  let before_line_count = 0
  let after_line_count = 0
  let reg_save = @@

  try
    let [lnum, col] = s:search_ns_form_pos()
    if lnum == -1 && col == -1
      call elin#internal#echom('No namespace found', 'ErrorMsg')
      return
    endif
    call cursor(lnum, col)
    keepjumps silent normal! dab

    let before_line_count = len(split(@@, '\r\?\n'))
    " if before_line_count == 1
    "   call iced#compat#deletebufline('%', line('.'), 1)
    " endif

    let lnum = line('.') - 1
    call append(lnum, split(new_ns, '\r\?\n'))
  finally
    let @@ = reg_save
    " if iced#nrepl#ns#util#search() != 0
    "   call iced#promise#wait(iced#format#current())
    "   call iced#nrepl#ns#eval({_ -> ''})
    " endif

    " NOTE: need to calculate lnum after calling `iced#format#current`
    let after_line_count = len(split(elin#internal#sexp#clojure#get_ns_form(), '\r\?\n'))

    let view['lnum'] = view['lnum'] + (after_line_count - before_line_count)
    call winrestview(view)
  endtry
endfunction
