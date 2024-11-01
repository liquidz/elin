let s:buf_name = 'elin_temporal_buffer'

function! elin#internal#buffer#temp#is_visible() abort
  return elin#internal#buffer#is_visible(s:buf_name)
endfunction

function! elin#internal#buffer#temp#open(...) abort
  if elin#internal#buffer#temp#is_visible()
    return
  endif

  let option = get(a:, 1, {})
  call elin#internal#buffer#open(s:buf_name, option)
endfunction

function! elin#internal#buffer#temp#close() abort
  call elin#internal#buffer#temp#clear()
  call elin#internal#buffer#close(s:buf_name)
endfunction

function! elin#internal#buffer#temp#ready() abort
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

  silent call deletebufline(s:buf_name, 1)
endfunction

function! elin#internal#buffer#temp#set(s) abort
  if trim(a:s) ==# ''
    call elin#internal#buffer#temp#close()
  else
    call elin#internal#buffer#temp#clear()
    call elin#internal#buffer#append(s:buf_name, a:s, {'scroll_to_top': v:true})
    call deletebufline(s:buf_name, 1)

    " Open aotumatically when info buffer is not opened
    if ! elin#internal#buffer#info#is_visible()
      call elin#internal#buffer#temp#open({
            \ 'opener': 'split',
            \ 'mods': 'belowright',
            \ 'height': &previewheight,
            \ })
    endif
  endif
endfunction

function! elin#internal#buffer#temp#clear() abort
  call elin#internal#buffer#clear(s:buf_name)
  call deletebufline(s:buf_name, 1)
endfunction
