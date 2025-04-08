interface PagingProps {
    currentPage: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
    onPageChange: (page: number) => void;
    onPageSizeChange: (pageSize: number) => void;
}

export function Paging({
                           currentPage,
                           totalPages,
                           totalElements,
                           pageSize,
                           onPageChange,
                           onPageSizeChange
                       }: PagingProps) {
    const pageSizeOptions = [10, 25, 50, 100];

        // Generate page numbers to display
        const getPageNumbers = () => {
            // If 3 or fewer total pages, show all pages
            if (totalPages <= 3) {
                return Array.from({length: totalPages}, (_, i) => i);
            }

            // Always show first, last, and pages around current
            const pages = [0]; // First page

            let startPage = Math.max(1, currentPage - 1);
            let endPage = Math.min(totalPages - 2, currentPage + 1);

            // Add ellipsis if there's a gap after first page
            if (startPage > 1) pages.push(-1); // Add ellipsis

            for (let i = startPage; i <= endPage; i++) {
                pages.push(i);
            }

            // Add ellipsis if there's a gap before last page
            if (endPage < totalPages - 2) pages.push(-2); // Add ellipsis

            pages.push(totalPages - 1); // Last page

            return pages;
        };

    return (
        <div>
            <div className="flex justify-between items-center my-6">
                <div className="flex-1">
                    {/* Empty div for spacing */}
                </div>
                <div className="flex items-center space-x-1">
                    <button
                        onClick={() => onPageChange(currentPage - 1)}
                        disabled={currentPage === 0}
                        className={`px-3 py-1 rounded-md text-sm ${currentPage === 0 ? 'text-gray-400 cursor-not-allowed' :
                            'text-orange-600 hover:bg-orange-100'}`}
                        aria-label="Previous page"
                    >
                        Previous
                    </button>

                    {getPageNumbers().map((pageNum, idx) => {
                        if (pageNum < 0) {
                            return (
                                <span key={`ellipsis-${idx}`} className="px-2">...</span>
                            );
                        }
                        return (
                            <button
                                key={`page-${pageNum}`}
                                onClick={() => onPageChange(pageNum)}
                                className={`px-3 py-1 rounded-md text-sm ${
                                    pageNum === currentPage
                                        ? 'bg-orange-600 text-white'
                                        : 'text-orange-600 hover:bg-orange-100'
                                }`}
                                aria-current={pageNum === currentPage ? 'page' : undefined}
                            >
                                {pageNum + 1}
                            </button>
                        );
                    })}

                    <button
                        onClick={() => onPageChange(currentPage + 1)}
                        disabled={currentPage === totalPages - 1}
                        className={`px-3 py-1 rounded-md ${currentPage === totalPages - 1 ?
                            'text-gray-400 cursor-not-allowed' : 'text-orange-600 hover:bg-orange-100'}`}
                        aria-label="Next page"
                    >
                        Next
                    </button>
                </div>
                <div className="flex-1 flex justify-end">
                    <select
                        value={pageSize}
                        onChange={(e) => onPageSizeChange(Number(e.target.value))}
                        className="px-2 py-1 rounded-md text-sm text-orange-600 border border-orange-200
                            focus:border-orange-600 focus:outline-none"
                        aria-label="Mappings per page"
                    >
                        <option value="" disabled>Mappings per page</option>
                        {pageSizeOptions.map(size => (
                            <option key={size} value={size} className="hover:bg-orange-100">
                                {size} per page
                            </option>
                        ))}
                    </select>
                </div>
            </div>
            <div className="text-center text-sm text-orange-600">
                Showing {pageSize} of {totalElements} mappings
            </div>
        </div>
    );


}