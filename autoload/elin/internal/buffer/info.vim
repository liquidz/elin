let s:buf_name = 'elin_info_buffer'
let s:title = ';; Elin Info Buffer'
let s:delimiter = ";; ====================\n\n"
let s:max_line = 1000

function! elin#internal#buffer#info#is_visible() abort
  return elin#internal#buffer#is_visible(s:buf_name)
endfunction

function! elin#internal#buffer#info#open(...) abort
  if elin#internal#buffer#info#is_visible()
    return
  endif
  if elin#internal#buffer#temp#is_visible()
    call elin#internal#buffer#temp#close()
  endif

  let option = get(a:, 1, {})
  call elin#internal#buffer#open(s:buf_name, option)
  call elin#internal#buffer#scroll_to_bottom(bufnr(s:buf_name))
endfunction

function! elin#internal#buffer#info#close() abort
  call elin#internal#buffer#close(s:buf_name)
endfunction

function! elin#internal#buffer#info#toggle(...) abort
  if elin#internal#buffer#info#is_visible()
    return elin#internal#buffer#info#close()
  endif
  return elin#internal#buffer#info#open(get(a:, 1, {}))
endfunction

function! elin#internal#buffer#info#ready() abort
  if bufnr(s:buf_name) != -1
    return
  endif
  silent execute printf(':split %s', s:buf_name)
  silent execute ':q'

  silent call setbufvar(s:buf_name, 'lsp_diagnostics_enabled', 0)
  silent call setbufvar(s:buf_name, '&bufhidden', 'hide')
  silent call setbufvar(s:buf_name, '&buflisted', 0)
  silent call setbufvar(s:buf_name, '&buftype', 'nofile')
  silent call setbufvar(s:buf_name, '&filetype', 'clojure')
  silent call setbufvar(s:buf_name, '&swapfile', 0)
  silent call setbufvar(s:buf_name, '&wrap', 0)

  silent call elin#internal#buffer#append(s:buf_name, s:title)
  silent call deletebufline(s:buf_name, 1)
endfunction

function! elin#internal#buffer#info#append(s) abort
  call elin#internal#buffer#append(s:buf_name, a:s, {'scroll_to_bottom': v:true})

  call elin#util#start_lazily('delete_old_lines', 1000, funcref('s:delete_old_lines'))
  if a:s !=# s:delimiter
    call elin#util#start_lazily('append_delimiter', 2000, {-> elin#internal#buffer#info#append(s:delimiter)})
  endif
endfunction

function! elin#internal#buffer#info#clear() abort
  call elin#internal#buffer#clear(s:buf_name)
  call elin#internal#buffer#append(s:buf_name, s:title)
  call deletebufline(s:buf_name, 1)
endfunction

function! s:delete_old_lines() abort
  let nr = bufnr(s:buf_name)
  let buflen = len(getbufline(nr, 0, '$'))
  if s:max_line > 0 && buflen > s:max_line
    let line_diff = buflen - s:max_line
    call deletebufline(s:buf_name, 1, line_diff)

    if elin#internal#buffer#info#is_visible()
      let current_window = winnr()
      try
        call elin#internal#buffer#focus_by_name(s:buf_name)
        let view = winsaveview()
        let view['topline'] = max([1, view['topline'] - line_diff])
        call winrestview(view)
      finally
        execute current_window . 'wincmd w'
      endtry
    endif
  endif
endfunction
