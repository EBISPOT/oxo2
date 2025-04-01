import { ErrorProps } from "./ErrorSlice";
import { ExclamationTriangleIcon } from "@heroicons/react/24/solid";

export function ErrorInfo(error: ErrorProps) {
    return (
        <div className="px-2">
            <div className="text-2xl mb-3 text-neutral-default">
                    <span>
                    <ExclamationTriangleIcon/>
                        <span> The following error: {error.message} occurred while doing {error.task}.
                        </span>
                    </span>
            </div>
        </div>
    )
}