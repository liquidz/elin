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
    try
      return luaeval('require("elin.sexpr").get_top_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
    catch
      return {'code': '', 'lnum': 0, 'col': 0}
    endtry
  endfunction

  function! s:get_list(lnum, col) abort
    try
      return luaeval('require("elin.sexpr").get_list(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
    catch
      return {'code': '', 'lnum': 0, 'col': 0}
    endtry
  endfunction

  function! s:get_top_list_range(lnum, col) abort
    try
      return luaeval('require("elin.sexpr").get_top_list_range(_A[1], _A[2])', [a:lnum - 1, a:col - 1])
    catch
      return {'start_col': -1, 'start_row': -1, 'end_col': -1, 'end_row': -1}
    endtry
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

  function! s:get_top_list_range(lnum, col) abort

    "if getline(a:lnum)[0] !=# '('
    "  let is_cancelled = v:true
    "  return
    "endif
    "call cursor(a:lnum, a:col)
    "keepjumps silent normal! vaby

    " FIXME
    return {'start_col': -1, 'start_row': -1, 'end_col': -1, 'end_row': -1}
  endfunction

endif

function! s:set_visual_marks(range) abort
  "call setpos("'<", [0, get(a:range, 'start_row', 0) + 1, get(a:range, 'start_col', 0) + 1, 0])
  call setpos("'<", [0, get(a:range, 'start_row', 0) + 1, get(a:range, 'start_col', 0), 0])
  "call setpos("'>", [0, get(a:range, 'end_row', 0) + 1, get(a:range, 'end_col', 0) + 1, 0])
  call setpos("'>", [0, get(a:range, 'end_row', 0) + 1, get(a:range, 'end_col', 0), 0])
endfunction

function! elin#internal#sexpr#replace_list_sexpr(lnum, col, new_sexpr) abort
  let context = elin#internal#context#save()
  let before_line_count = 0
  let after_line_count = 0
  let reg_save = @@
  let is_cancelled = v:false
  let signs = elin#internal#sign#list_in_buffer()

  try
    let top_list_range = s:get_top_list_range(a:lnum, a:col)
    echom printf('FIXME range %s', top_list_range)
    if get(top_list_range, 'start_col', -1) == -1 && get(top_list_range, 'start_row', -1) == -1
      let is_cancelled = v:true
      return
    endif
    call s:set_visual_marks(top_list_range)
    keepjumps silent normal! gvy

    let before_sexpr = @@
    if before_sexpr ==# ''
      let is_cancelled = v:true
      return
    endif
    let before_line_count = len(split(before_sexpr, '\r\?\n'))

    let @@ = trim(a:new_sexpr)
    keepjumps silent normal! gv"0p
  finally
    let @@ = reg_save

    if ! is_cancelled
      call elin#internal#sign#refresh({'signs': signs})

      let new_line_count = len(split(trim(a:new_sexpr), '\r\?\n'))
      let context['view']['lnum'] = context['view']['lnum'] + (new_line_count - before_line_count)
    endif
    call elin#internal#context#restore(context)
  endtry
endfunction
