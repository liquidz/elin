function! elin#internal#sexp#get_top_list(lnum, col) abort
  return s:get_top_list(a:lnum, a:col)
endfunction

function! elin#internal#sexp#get_list(lnum, col) abort
  return s:get_list(a:lnum, a:col)
endfunction

function! elin#internal#sexp#get_expr(lnum, col) abort
  let view = winsaveview()
  try
    call cursor(a:lnum, a:col)
    return {'code': elin#util#cword(), 'lnum': a:lnum, 'col': a:col}
  finally
    call winrestview(view)
  endtry
endfunction

function! s:skip_in_string_or_comment() abort
  if synIDattr(synID(line('.'), col('.'), 0), 'name') =~? 'string\|comment'
    return 1
  endif
  return 0
endfunction

if has('nvim') && exists('*nvim_get_runtime_file') && len(nvim_get_runtime_file('parser', v:true)) > 0

  function! s:get_top_list(lnum, col) abort
    return luaeval('require("vim-elin.sexp").get_top_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction

  function! s:get_list(lnum, col) abort
    return luaeval('require("vim-elin.sexp").get_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction

else

  function! s:get_top_list(lnum, col) abort
    let view = winsaveview()
    try
      call cursor(a:lnum, a:col)
      let start_lnum = search('^\S', 'cbW')
      if start_lnum == 0
        return ''
      endif
      let end_lnum = searchpair('(', '', ')', 'W', funcref('s:skip_in_string_or_comment'))
      if end_lnum <= 0
        return ''
      endif

      return {'code': join(getline(start_lnum, end_lnum), "\n"), 'lnum': start_lnum, 'col': 1}
    finally
      call winrestview(view)
    endtry
  endfunction

  function! s:get_list(lnum, col) abort
    let view = winsaveview()
    try
      call cursor(a:lnum, a:col)
      let start_pos = searchpairpos('(', '', ')', 'cbW', funcref('s:skip_in_string_or_comment'))
      if start_pos == [0, 0]
        return ''
      endif
      let end_pos = searchpairpos('(', '', ')', 'W', funcref('s:skip_in_string_or_comment'))
      if end_pos == [0, 0]
        return ''
      endif

      let lines = getline(start_pos[0], end_pos[0])
      if len(lines) == 1
        let lines[0] = strpart(lines[0], start_pos[1] - 1)
        let lines[0] = strpart(lines[-1], 0, end_pos[1] - start_pos[1] + 1)
      else
        let lines[0] = strpart(lines[0], start_pos[1] - 1)
        let lines[-1] = strpart(lines[-1], 0, end_pos[1])
      endif

      return {'code': join(lines, "\n"), 'lnum': start_pos[0], 'col': start_pos[1]}
    finally
      call winrestview(view)
    endtry
  endfunction

endif
