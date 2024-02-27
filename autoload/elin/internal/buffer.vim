function! elin#internal#buffer#focus_by_win_nr(win_nr) abort
  execute printf('%dwincmd w', a:win_nr)
endfunction

function! elin#internal#buffer#focus_by_name(buf_name) abort
  let win_nr = bufwinnr(a:buf_name)
  if win_nr == -1
    return
  endif
  return elin#internal#buffer#focus_by_win_nr(win_nr)
endfunction

function! elin#internal#buffer#is_visible(buf_name) abort
  return bufwinnr(a:buf_name) != -1
endfunction

function! elin#internal#buffer#open(buf_name, option) abort
  let current_window = winnr()

  let opt = type(a:option) == v:t_dict ? a:option : {}
  let mods = get(opt, 'mods', 'vertical')
  let opener = get(opt, 'opener', 'split')

  try
    let &eventignore = 'WinEnter,WinLeave,BufEnter,BufLeave'
    execute printf('%s %s %s', mods, opener, a:buf_name)
  finally
    let &eventignore = ''
    call elin#internal#buffer#focus_by_win_nr(current_window)
  endtry
endfunction

function! elin#internal#buffer#append_line(buf_name, one_line) abort
  if has('nvim')
    return nvim_buf_set_lines(bufnr(a:buf_name), -1, -1, 0, [a:one_line])
  else
    call appendbufline(a:buf_name, '$', a:one_line)
  endif
endfunction

function! s:delete_color_code(s) abort
  return substitute(a:s, '\[[0-9;]*m', '', 'g')
endfunction

function! s:scroll_to_bottom(nr) abort
  let current_window = winnr()
  let last_window = winnr('#')
  try
    let &eventignore = 'WinEnter,WinLeave,BufEnter,BufLeave'
    call elin#internal#buffer#focus_by_win_nr(bufwinnr(a:nr))
    silent normal! G
  finally
    " Preserve the user's last visited window by focusing to it first (PR #187)
    call elin#internal#buffer#focus_by_win_nr(last_window)
    call elin#internal#buffer#focus_by_win_nr(current_window)
    let &eventignore = ''
  endtry
endfunction

function! elin#internal#buffer#append(buf_name, s, ...) abort
  let nr = bufnr(a:buf_name)
  if nr < 0 | return | endif
  let opt = get(a:, 1, {})

  for line in split(s:delete_color_code(a:s), '\r\?\n')
    silent call elin#internal#buffer#append_line(a:buf_name, line)
  endfor

  if get(opt, 'scroll_to_bottom', v:false)
        \ && elin#internal#buffer#is_visible(a:buf_name)
        \ && bufnr('%') != nr
    call elin#util#start_lazily('scroll_to_bottom', 500, funcref('s:scroll_to_bottom', [nr]))
  endif
endfunction

function! elin#internal#buffer#clear(buf_name) abort
  return deletebufline(a:buf_name, 1, "$")
endfunction

function! elin#internal#buffer#set(buf_name, lines) abort
  for line in reverse(copy(a:lines))
    call appendbufline(a:buf_name, '0', line)
  endfor
endfunction

function! elin#internal#buffer#close(buf_name) abort
  if !elin#internal#buffer#is_visible(a:buf_name)
    return
  endif

  let current_window = winnr()
  let target_window = bufwinnr(a:buf_name)
  if current_window != target_window
    call elin#internal#buffer#focus_by_win_nr(target_window)
  endif

  silent execute ':q'

  if target_window > current_window
    call elin#internal#buffer#focus_by_win_nr(current_window)
  endif
endfunction
