
function! elin#internal#virtual_text#set(text, ...) abort
  let opt = get(a:, 1, {})
  let bufnr = get(opt, 'bufnr', bufnr('%'))
  let lnum = get(opt, 'lnum', line('.'))
  let hl = get(opt, 'highlight', 'Normal')
  let align = get(opt, 'align', 'after')
  return s:set(bufnr, a:text, lnum, hl, align)
endfunction

function! elin#internal#virtual_text#clear(...) abort
  let opt = get(a:, 1, {})
  let bufnr = get(opt, 'bufnr', bufnr('%'))
  let lnum = get(opt, 'lnum', v:null)

  if lnum is# v:null
    return s:clear(bufnr, 1, line('$'))
  else
    return s:clear(bufnr, lnum, lnum + 1)
  endif
endfunction

if has('nvim')
  let s:namespace = nvim_create_namespace('elin_virtual_text_namespace')

  function! s:text_align_to_virt_text_pos(align) abort
    return get({'after': 'eol', 'right': 'right_align'}, a:align, 'eol')
  endfunction

  function! s:set(bufnr, text, lnum, highlight, align) abort
    call s:clear(a:bufnr, a:lnum - 1, a:lnum)
    return nvim_buf_set_extmark(a:bufnr, s:namespace, a:lnum - 1, 0, {
          \ 'virt_text': [[a:text, a:highlight]],
          \ 'virt_text_pos': s:text_align_to_virt_text_pos(a:align)
          \ })
  endfunction

  function! s:clear(bufnr, start_lnum, end_lnum) abort
    return nvim_buf_clear_namespace(a:bufnr, s:namespace, a:start_lnum - 1, a:end_lnum - 1)
  endfunction


else

  let s:textprop_type = 'elin_virtual_text_namespace'
  call prop_type_delete(s:textprop_type, {})
  call prop_type_add(s:textprop_type, {})

  function! s:set(bufnr, text, lnum, highlight, align) abort
    echom printf('FIXME %s', a:lnum)
    call prop_type_change(s:textprop_type, {'highlight': a:highlight})
    call prop_add(a:lnum, 0, {
          \ 'type': s:textprop_type,
          \ 'bufnr': a:bufnr,
          \ 'text': a:text,
          \ 'text_align': a:align,
          \ 'text_padding_left': 1,
          \ })
    redraw
  endfunction

  function! s:clear(bufnr, start_lnum, end_lnum) abort
    return prop_remove({
          \ 'type': s:textprop_type,
          \ 'bufnr': a:bufnr,
          \ 'all': v:true,
          \ }, a:start_lnum, a:end_lnum)
  endfunction

endif
