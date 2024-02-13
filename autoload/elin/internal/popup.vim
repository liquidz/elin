let s:last_winid = v:null
let s:context_name = 'elin_context'
let s:default_filetype = 'clojure'

function! elin#internal#popup#open(texts, ...) abort
  let opts = get(a:, 1, {})
  return s:open(a:texts, opts)
endfunction

function! elin#internal#popup#move(winid, lnum, col) abort
  return s:move(a:winid, a:lnum, a:col)
endfunction

function! elin#internal#popup#set_texts(winid, texts) abort
  return s:set_texts(a:winid, a:texts)
endfunction

function! elin#internal#popup#close(winid) abort
  return s:close(a:winid)
endfunction

function! s:init_win(winid, opts) abort
  let has_moved = has_key(a:opts, 'moved')

  let context = get(a:opts, s:context_name, {})
  let context['__lnum'] = line('.')
  if has_moved
    let context['__moved'] = a:opts['moved']
    let context['__reltime'] = reltime()
  endif
  call setwinvar(a:winid, s:context_name, context)
  call setwinvar(a:winid, '&signcolumn', 'no')

  let bufnr = winbufnr(a:winid)
  " HACK: To avoid vim-lsp activation
  try
    " Neovim 0.8.0 throws 'Invalid argument' error
    " Most meovim users uses built-in LSP, so this hack may be unnecessary
    call setbufvar(bufnr, '&buftype', 'terminal')
  catch
  endtry

  call setbufvar(bufnr, '&filetype', get(a:opts, 'filetype', s:default_filetype))
  call setbufvar(bufnr, '&swapfile', 0)
  call setbufvar(bufnr, '&wrap', 0)
  call setbufvar(bufnr, '&foldlevel', 9999)

  if has('nvim') && has_moved
    call setbufvar(bufnr, '&winhl', 'Normal:NormalFloat')
    if has_moved
      aug elin_nvim_popup_moved
        au!
        au CursorMoved,CursorMovedI <buffer> call s:__moved()
      aug END
    endif
  endif
endfunction

function! s:split_by_length(s, len) abort
  let s = a:s
  let res = []
  while ! empty(s)
    let res += [strpart(s, 0, a:len)]
    let s = strpart(s, a:len)
  endwhile
  return res
endfunction

function! s:calculate(texts, opts) abort
  let wininfo = getwininfo(win_getid())[0]
  let max_width = float2nr(wininfo['width'] * 0.95)
  let max_height = float2nr((&lines - &cmdheight) * 0.3)

  let width = max(map(copy(a:texts), {_, v -> len(v)})) + 2
  let width = min([width, max_width])
  let texts = get(a:opts, 'wrap', 0)
        \ ? flatten(map(copy(a:texts), {_, text -> s:split_by_length(text, width)}))
        \ : a:texts
  let height = min([len(texts), max_height])

  " line
  let line = get(a:opts, 'line', winline())
  if type(line) == v:t_number
    let line = line - 1 + wininfo['winrow'] - 1
  else
    if line ==# 'near-cursor'
      " NOTE: `+ 5` make the popup window not too low
      if winline() + height + 5 > &lines
        let line = winline() - height
      else
        let line = winline() + wininfo['winrow'] - 1
      endif
    elseif line ==# 'top'
      let line = wininfo['winrow'] - 1
    elseif line ==# 'bottom'
      let line = wininfo['winrow'] +  wininfo['height'] - height - 1
    else
      throw printf('Invalid popup line parameter: %s', line)
    endif
  endif

  " col
  let col = get(a:opts, 'col', wincol())
  if type(col) == v:t_number
    let col = col - 1 + wininfo['wincol']
  else
    if col ==# 'right'
      let col = wininfo['width'] - width + wininfo['wincol']
    elseif col ==# 'near-cursor'
      let col = wincol() - 1 + wininfo['wincol']
    else
      throw printf('Invalid popup col parameter: %s', col)
    endif
  endif

  return {'texts': texts, 'line': line, 'col': col, 'width': width, 'max_width': max_width, 'height': height, 'max_height': max_height}
endfunction

if has('nvim')

  function! s:open(texts, opts) abort
    call s:close(s:last_winid)
    let bufnr = nvim_create_buf(0, 1)
    if bufnr < 0 || type(a:texts) != v:t_list || empty(a:texts)
      return
    endif

    let calculated = s:calculate(a:texts, a:opts)
    let win_opts = {
          \ 'relative': 'editor',
          \ 'row': calculated['line'],
          \ 'col': calculated['col'],
          \ 'width': calculated['width'],
          \ 'height': calculated['height'],
          \ 'style': get(a:opts, 'style', 'minimal'),
          \ 'focusable': v:false,
          \ 'noautocmd': v:true,
          \ }

    if has_key(a:opts, 'border')
      let border = get(a:opts, 'border')
      let win_opts['border'] = empty(border)
            \ ? 'double'
            \ : border
    endif

    " Open popup
    call nvim_buf_set_lines(bufnr, 0, len(calculated['texts']), 0, calculated['texts'])
    let winid = nvim_open_win(bufnr, v:false, win_opts)
    call s:init_win(winid, a:opts)

    let s:last_winid = winid
    return winid
  endfunction

  function! s:__moved() abort
    let context = getwinvar(s:last_winid, s:context_name, {})
    let moved = get(context, '__moved', '')
    let base_line = get(context, '__lnum', 0)
    let line = line('.')
    let col = col('.')
    let elapsed = reltimefloat(reltime(get(context, '__reltime', reltime())))

    " WARN: only supports 'any' and column list
    if empty(moved)
      return
    elseif type(moved) == v:t_string && moved ==# 'any' && elapsed > 0.1
      aug elin_nvim_popup_moved
        au!
      aug END
      return s:close(s:last_winid)
    elseif type(moved) == v:t_list && (line != base_line || col < moved[0] || col > moved[1]) && elapsed > 0.1
      aug elin_nvim_popup_moved
        au!
      aug END
      return s:close(s:last_winid)
    endif
  endfunction

  function! s:move(winid, lnum, col) abort
    let win_opts = nvim_win_get_config(a:winid)
    let win_opts['relative'] = 'editor'
    let wininfo = getwininfo(win_getid())[0]

    if a:lnum isnot# v:null
      let win_opts['row'] = a:lnum + wininfo['winrow'] - 1
    endif

    if a:col isnot# v:null
      let win_opts['col'] = a:col + wininfo['wincol']
    endif

    call nvim_win_set_config(a:winid, win_opts)
  endfunction

  function! s:set_texts(winid, texts) abort
    let bufnr = winbufnr(a:winid)
    let info = getbufinfo(bufnr)[0]
    let variables = get(info, 'variables', {})
    let linecount = get(variables, 'linecount', len(a:texts))
    call nvim_buf_set_lines(bufnr, 0, linecount, 0, a:texts)
  endfunction

  function! s:close(winid) abort
    try
      call nvim_win_close(a:winid, 0)
    catch
    endtry
  endfunction

else

  function! s:open(texts, opts) abort
    call s:close(s:last_winid)

    let calculated = s:calculate(a:texts, a:opts)
    let org_line = get(a:opts, 'line')
    let line = calculated['line']

    if type(org_line) == v:t_number || (org_line !=# 'top' && org_line !=# 'bottom')
      let line = (line >= winline()) ? line + 1 : line - 1
    elseif type(org_line) == v:t_string && org_line ==# 'top'
      let line = line + 1
    elseif type(org_line) == v:t_string && org_line ==# 'bottom'
      let line = line - 1
    endif

    let win_opts = {
          \ 'line': max([1, line]),
          \ 'col': calculated['col'],
          \ 'minwidth': calculated['width'],
          \ 'maxwidth': calculated['max_width'],
          \ 'minheight': calculated['height'],
          \ 'maxheight': calculated['max_height'],
          \ }

    call extend(win_opts, elin#util#select_keys(a:opts,
          \ ['highlight', 'border', 'borderchars', 'borderhighlight',
          \  'moved', 'wrap', 'textprop', 'textpropid', 'mask']))

    " Open popup
    let winid = popup_create(a:texts, win_opts)
    call s:init_win(winid, a:opts)

    let s:last_winid = winid
    return winid
  endfunction

  function! s:move(winid, lnum, col) abort
    let wininfo = getwininfo(win_getid())[0]
    let options = {}
    if a:lnum isnot# v:null
      let options['line'] = a:lnum + wininfo['winrow'] - 1
    endif
    if a:col isnot# v:null
      let options['col'] = a:col + wininfo['wincol']
    endif
    call popup_move(a:winid, options)
  endfunction

  function! s:set_texts(winid, texts) abort
    call popup_settext(a:winid, a:texts)
  endfunction

  function! s:close(winid) abort
    return popup_close(a:winid)
  endfunction

endif

function! Test() abort
  let g:id = elin#internal#popup#open(
        \ ['foo', 'bar', 'baz'],
        \ {'line': 'near-cursor', 'border': [], 'moved': 'any'},
        \ )
endfunction
