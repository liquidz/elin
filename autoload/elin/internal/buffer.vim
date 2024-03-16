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

let s:append_buffer = {}

function! s:append(buf_name, option) abort
  let lines = copy(get(s:append_buffer, a:buf_name, []))
  let s:append_buffer[a:buf_name] = []

  if has('nvim')
    call nvim_buf_set_lines(bufnr(a:buf_name), -1, -1, 0, lines)
  else
    for line in lines
      silent call appendbufline(a:buf_name, '$', line)
    endfor
  endif

  let nr = bufnr(a:buf_name)
  if get(a:option, 'scroll_to_bottom', v:false)
        \ && elin#internal#buffer#is_visible(a:buf_name)
        \ && bufnr('%') != nr
    call elin#util#start_lazily('scroll_to_bottom', 500, funcref('s:scroll_to_bottom', [nr]))
  endif
endfunction

function! elin#internal#buffer#append(buf_name, s, ...) abort
  let opt = get(a:, 1, {})
  let lines = split(s:delete_color_code(a:s), '\r\?\n')
  if len(lines) == 0 | return | endif

  if ! has_key(s:append_buffer, a:buf_name)
    let s:append_buffer[a:buf_name] = []
  endif
  let s:append_buffer[a:buf_name] += lines

  let id = printf('append_%s', a:buf_name)
  call elin#util#start_lazily(id, 100, funcref('s:append', [a:buf_name, opt] ))
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
