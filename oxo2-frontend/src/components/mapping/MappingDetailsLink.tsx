import {forwardRef} from 'react';
import {Link, type LinkProps, useLocation} from 'react-router';

/** Keep the originating results URL while moving between individual mapping records. */
export const MappingDetailsLink = forwardRef<HTMLAnchorElement, LinkProps>(function MappingDetailsLink(
    {state, ...props}, ref,
) {
    const location = useLocation();
    const returnTo = location.pathname.startsWith('/mapping/')
        ? location.state?.returnTo
        : `${location.pathname}${location.search}${location.hash}`;

    return <Link {...props} ref={ref} state={{...state, returnTo}} />;
});
