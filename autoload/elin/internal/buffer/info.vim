let s:buf_name = 'elin_info_buffer'
let s:title = ';; Elin Info Buffer'
let s:delimiter = ';; ----------'

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

  call setbufvar(s:buf_name, 'lsp_diagnostics_enabled', 0)
  call setbufvar(s:buf_name, '&bufhidden', 'hide')
  call setbufvar(s:buf_name, '&buflisted', 0)
  call setbufvar(s:buf_name, '&buftype', 'nofile')
  call setbufvar(s:buf_name, '&filetype', 'clojure')
  call setbufvar(s:buf_name, '&swapfile', 0)
  call setbufvar(s:buf_name, '&wrap', 0)

  call elin#internal#buffer#append(s:buf_name, s:title)
  call deletebufline(s:buf_name, 1)
endfunction

function! elin#internal#buffer#info#append(s) abort
  call elin#internal#buffer#append(s:buf_name, a:s, {'scroll_to_bottom': v:true})
  if a:s !=# s:delimiter
    call elin#util#start_lazily('append_delimiter', 500, {-> elin#internal#buffer#info#append(s:delimiter)})
  endif
endfunction

function! elin#internal#buffer#info#clear() abort
  call elin#internal#buffer#clear(s:buf_name)
  call elin#internal#buffer#append(s:buf_name, s:title)
  call deletebufline(s:buf_name, 1)
endfunction
