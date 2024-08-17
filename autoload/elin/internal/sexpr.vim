function! elin#internal#sexpr#get_top_list(path, lnum, col) abort
  let context = elin#internal#context#save()
  try
    call s:jump_if_path_exists(a:path)
    return s:get_top_list(a:lnum, a:col)
  finally
    call elin#internal#context#restore(context)
  endtry
endfunction

function! elin#internal#sexpr#get_list(path, lnum, col) abort
  let context = elin#internal#context#save()
  try
    call s:jump_if_path_exists(a:path)
    return s:get_list(a:lnum, a:col)
  finally
    call elin#internal#context#restore(context)
  endtry
endfunction

function! elin#internal#sexpr#get_expr(path, lnum, col) abort
  let context = elin#internal#context#save()
  try
    call s:jump_if_path_exists(a:path)
    call cursor(a:lnum, a:col)
    return {'code': elin#util#cword(), 'lnum': a:lnum, 'col': a:col}
  finally
    call elin#internal#context#restore(context)
  endtry
endfunction

function! s:skip_in_string_or_comment() abort
  if synIDattr(synID(line('.'), col('.'), 0), 'name') =~? 'string\|comment'
    return 1
  endif
  return 0
endfunction

function! s:jump_if_path_exists(path) abort
  if a:path !=# '' && expand('%:p') !=# a:path && filereadable(a:path)
    execute printf(':keepjumps edit %s', a:path)
  endif
endfunction

if has('nvim') && exists('*nvim_get_runtime_file') && len(nvim_get_runtime_file('parser', v:true)) > 0

  function! s:get_top_list(lnum, col) abort
    return luaeval('require("vim-elin.sexpr").get_top_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction

  function! s:get_list(lnum, col) abort
    return luaeval('require("vim-elin.sexpr").get_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
  endfunction

else

  function! s:get_top_list(lnum, col) abort
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
  endfunction

  function! s:get_list(lnum, col) abort
    call cursor(a:lnum, a:col)
    let start_pos = searchpairpos('(', '', ')', 'cbW', funcref('s:skip_in_string_or_comment'))
    if start_pos == [0, 0]
      return {'code': '', 'lnum': 0, 'col': 0}
    endif
    let end_pos = searchpairpos('(', '', ')', 'W', funcref('s:skip_in_string_or_comment'))
    if end_pos == [0, 0]
      return {'code': '', 'lnum': 0, 'col': 0}
    endif

    " TODO use getregion
    " https://zenn.dev/kawarimidoll/articles/4357f07f210d2f
    let lines = getline(start_pos[0], end_pos[0])
    if len(lines) == 1
      let lines[0] = strpart(lines[0], start_pos[1] - 1)
      let lines[0] = strpart(lines[-1], 0, end_pos[1] - start_pos[1] + 1)
    else
      let lines[0] = strpart(lines[0], start_pos[1] - 1)
      let lines[-1] = strpart(lines[-1], 0, end_pos[1])
    endif

    return {'code': join(lines, "\n"), 'lnum': start_pos[0], 'col': start_pos[1]}
  endfunction

endif

function! elin#internal#sexpr#replace_list_sexpr(lnum, col, new_sexpr) abort
  let context = elin#internal#context#save()
  let before_line_count = 0
  let after_line_count = 0
  let reg_save = @@

  try
    call cursor(a:lnum, a:col)

    keepjumps silent normal! vaby
    let before_sexpr = @@
    if before_sexpr ==# ''
      return
    endif
    let before_line_count = len(split(before_sexpr, '\r\?\n'))

    let @@ = trim(a:new_sexpr)
    keepjumps silent normal! gv"0p
  finally
    let @@ = reg_save

    let new_line_count = len(split(trim(a:new_sexpr), '\r\?\n'))
    let view['lnum'] = view['lnum'] + (new_line_count - before_line_count)
    call elin#internal#context#restore(context)
  endtry
endfunction
